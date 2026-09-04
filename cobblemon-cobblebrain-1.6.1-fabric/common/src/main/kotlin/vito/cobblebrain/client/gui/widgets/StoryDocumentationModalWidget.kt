package vito.cobblebrain.client.gui.widgets

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component

/**
 * Interactive Integrated Documentation & Node Reference Manual.
 * Fully verified against the active CobbleBrain engine and inspector implementation.
 * Organized into categorized folders with instant search, rich formatting,
 * accurate inputs/outputs, parameters breakdown, and pro tips.
 */
class StoryDocumentationModalWidget(
    val font: Font,
    val screenW: Int,
    val screenH: Int,
    val onClose: () -> Unit
) {
    val modalW = 560.coerceAtMost(screenW - 20)
    val modalH = 360.coerceAtMost(screenH - 20)
    val modalX = (screenW - modalW) / 2
    val modalY = (screenH - modalH) / 2

    // Sidebar dimensions
    private val sidebarW = 175
    private val sidebarX = modalX + 8
    private val sidebarY = modalY + 28
    private val sidebarH = modalH - 36

    // Content Panel dimensions
    private val contentX = sidebarX + sidebarW + 8
    private val contentY = sidebarY
    private val contentW = modalW - sidebarW - 24
    private val contentH = sidebarH

    // Search Box & Scrolls
    private val searchBox: EditBox
    private var sidebarScroll: Float = 0f
    private var contentScroll: Float = 0f
    private var isDraggingContentScroll: Boolean = false
    private var isDraggingSidebarScroll: Boolean = false
    private var dragStartMouseY: Double = 0.0
    private var dragStartContentScroll: Float = 0f
    private var dragStartSidebarScroll: Float = 0f
    private var lastContentTotalH: Float = 0f
    private var lastSidebarTotalH: Float = 0f

    // Data Structures
    data class GuideItem(
        val id: String,
        val folderId: String,
        val icon: String,
        val title: String,
        val subtitle: String,
        val inputs: String? = null,
        val outputs: String? = null,
        val purpose: String,
        val description: String,
        val parameters: List<String> = emptyList(),
        val proTip: String? = null
    )

    data class GuideFolder(
        val id: String,
        val icon: String,
        val name: String,
        var isExpanded: Boolean = true
    )

    private val folders = listOf(
        GuideFolder("editor", "📁", "Editor Areas & Tools"),
        GuideFolder("flow", "📁", "Blocks: Flow & Scenes"),
        GuideFolder("logic", "📁", "Blocks: Logic & Control"),
        GuideFolder("world", "📁", "Blocks: World & Interaction")
    )

    private val items = listOf(
        // ==========================================
        // FOLDER 1: EDITOR AREAS & TOOLS
        // ==========================================
        GuideItem(
            id = "top_bar",
            folderId = "editor",
            icon = "🖥️",
            title = "Top Bar & Menus",
            subtitle = "File, Add, System menus & breadcrumb navigation",
            purpose = "Provides project management, block creation, settings, and navigation.",
            description = "• File Menu: Save Story (Ctrl+S), Load Story (opens story selector), Export to ZIP (creates playable package via native file dialog), and Project Metadata.\n" +
                "• Add Menu: Quickly create a New Scene Frame, add common nodes (Dialogue, Move Entity, Story Tag, Var Get/Set), open Variable Manager, or open the full Block Palette.\n" +
                "• System Menu: Open Editor Settings or this Integrated Node Guide.\n" +
                "• Breadcrumbs & Tabs: Located directly below the top bar. Shows open Scenes and active Construction tabs. Click any tab to switch focus or click 'X' to close.\n" +
                "• Status Bar: Brief notifications appear in the bottom-left corner confirming actions (saves, copies, deletions).",
            parameters = listOf(
                "File Menu: Save, Load, Export ZIP, Metadata",
                "Add Menu: Scenes, Common Nodes, Variable Manager, Palette",
                "System Menu: Settings, Guide",
                "Breadcrumbs: Multi-tab navigation between scenes and constructions"
            ),
            proTip = "Use 'Export to ZIP' to generate a self-contained storypack that players can place in 'cobblebrain/storypacks/' to play immediately without extraction!"
        ),
        GuideItem(
            id = "canvas_controls",
            folderId = "editor",
            icon = "🕹️",
            title = "Canvas & Shortcuts",
            subtitle = "Pan, zoom, hotkeys & mouse interactions",
            purpose = "Controls for navigating and editing the infinite node graph.",
            description = "• Canvas Pan: Hold Right-Click or Middle-Click and drag on empty canvas to move your view.\n" +
                "• Zoom: Scroll mouse wheel on the canvas to zoom in and out smoothly between 25% and 200%.\n" +
                "• Node Placement: Pick a block from the palette; a semi-transparent ghost block follows your cursor. Left-Click anywhere on canvas to place it.\n" +
                "• Wire Connections: Drag a line from an OUT port circle to a compatible IN port circle. Click a wire or select a connected node and press Delete to remove.\n" +
                "• Marquee Selection: Hold Left-Click on empty canvas and drag a box across multiple blocks to select them together.\n" +
                "• Right-Click Context Menu: Right-click a node (Delete, Duplicate, Detach, Copy/Paste Data, Reset Properties, Disconnect Ports), a scene (Delete, Duplicate, Clear Connections), or empty canvas (Add Node, Paste, Save).",
            parameters = listOf(
                "Ctrl + C: Copy selected node(s) and connections",
                "Ctrl + V: Paste copied node(s) at cursor position",
                "Ctrl + A: Select all nodes on the active canvas",
                "Delete / Backspace: Delete selected nodes or wires",
                "Escape: Cancel placement, close menus, close inspector, or exit"
            ),
            proTip = "Right-click any node and choose 'Duplicate Block' to quickly clone complex configured nodes with all their parameters intact!"
        ),
        GuideItem(
            id = "scenes_architecture",
            folderId = "editor",
            icon = "🎬",
            title = "Scenes & Architecture",
            subtitle = "Global scene graph & modular scene files",
            purpose = "Structures large story campaigns into isolated, modular chapters.",
            description = "• Global Scene: The master graph where Scene blocks are placed and linked together with connections from scene OUT to scene IN.\n" +
                "• Modular Persistence: Each scene is stored in its own dedicated JSON file inside 'scenes/', preventing file bloat and making team collaboration easy.\n" +
                "• Scene Lifecycle: Inside each scene, execution starts at the BEGIN_SCENE node. When an END_SCENE node is reached, the scene terminates and fires the scene's outer OUT port on the Global canvas.\n" +
                "• Double-Click Entry: Double-click any scene node on the Global canvas to enter its internal graph. Use the top breadcrumb tabs to return to the Global canvas.",
            parameters = listOf(
                "Scene Title & Description",
                "Start Scene / End Scene status",
                "Dedicated scene JSON files in scenes/ directory"
            ),
            proTip = "Keep each scene focused on a single narrative beat (e.g. 'Intro_Dialogue', 'Gym_Battle', 'Post_Battle_Rewards') to make debugging easy!"
        ),
        GuideItem(
            id = "node_inspector",
            folderId = "editor",
            icon = "📦",
            title = "Node Inspector Panel",
            subtitle = "Title, timings, pre/post delay & block properties",
            purpose = "Sidebar on the right for editing properties of the currently selected node.",
            description = "• Title & Header: Displays the block icon, node type, and an editable title field.\n" +
                "• Color Theme: Node colors are automatically determined by their node type for instant visual clarity on the canvas.\n" +
                "• Pre-Delay (ticks): Pauses story execution for N ticks (20 ticks = 1 second) BEFORE the node performs its action.\n" +
                "• Post-Delay (ticks): Pauses story execution for N ticks AFTER the node performs its action, before firing output ports.\n" +
                "• Contextual Parameters: Dynamically shows options tailored to the node type (e.g. texture pickers, sound selectors, coordinate pickers, condition builders).",
            parameters = listOf(
                "Node Title: Custom display label",
                "Pre-Delay: Delay in ticks before execution",
                "Post-Delay: Delay in ticks after execution",
                "Custom fields tailored to each block type"
            ),
            proTip = "Use Pre-Delay and Post-Delay directly in dialogue and action nodes to achieve cinematic pacing without cluttering your canvas with extra Timer nodes!"
        ),
        GuideItem(
            id = "variables_scopes",
            folderId = "editor",
            icon = "📊",
            title = "Variables & Scopes",
            subtitle = "Global vs local memory, data types & interpolation",
            purpose = "Store quest flags, counters, player choices, and state.",
            description = "• Supported Types:\n" +
                "  - BOOLEAN: true or false flags.\n" +
                "  - NUMBER: Integers or decimals (e.g. 0, 10, 25.5).\n" +
                "  - STRING: Text strings.\n" +
                "  - LIST: Comma-separated lists of values (e.g. 'badge1, badge2, badge3').\n" +
                "• Scopes:\n" +
                "  - GLOBAL: Shared across all scenes in the storypack.\n" +
                "  - SCENE_LOCAL: Isolated strictly to the active scene, automatically cleared when the scene finishes.\n" +
                "• Text Interpolation: Use <var:variable_name> inside dialogues and titles, or {var_name} inside Command nodes to dynamically insert the variable's value!",
            parameters = listOf(
                "Types: BOOLEAN, NUMBER, STRING, LIST",
                "Scopes: GLOBAL, SCENE_LOCAL",
                "Text Tag: <var:variable_name>",
                "Command Token: {var_name}"
            ),
            proTip = "Use LIST variables with VARIABLE_SET (ADD/REMOVE) to track player inventory items or collected artifacts across your story!"
        ),
        GuideItem(
            id = "testing_debug",
            folderId = "editor",
            icon = "🧪",
            title = "Live Testing & Debug Controls",
            subtitle = "Play test, range test, pause/resume & active node focus",
            purpose = "Run, inspect, and debug your story in real-time inside the active Minecraft world.",
            description = "Located in the bottom-right corner of the editor:\n" +
                "• [▶ Test ▾] Menu:\n" +
                "  - Live Test: Runs the story from the beginning in the active world.\n" +
                "  - Start All Triggers: Activates all triggers in the scene simultaneously.\n" +
                "  - Range Test Selection: Click to select a Start Block and an End Block on the canvas, running ONLY that specific sequence without replaying previous chapters.\n" +
                "• [⏸ Pause / ▶ Resume]: Temporarily freezes execution so you can inspect world states, then resumes right where it left off.\n" +
                "• [⏹ Stop]: Immediately halts story execution and clears active session memory.\n" +
                "• [🎯 Focus #]: Appears whenever a block is currently executing; click to center the camera on the active running node.",
            parameters = listOf(
                "Live Test: Full story execution",
                "Range Test: Isolate sequence between two chosen nodes",
                "Pause / Resume / Stop controls",
                "Focus active running block"
            ),
            proTip = "During test execution, running nodes glow with a bright yellow outline on the canvas, making it easy to track execution flow!"
        ),

        // ==========================================
        // FOLDER 2: BLOCKS - FLOW & SCENES
        // ==========================================
        GuideItem(
            id = "node_begin_scene",
            folderId = "flow",
            icon = "🟢",
            title = "BEGIN_SCENE",
            subtitle = "Mandatory scene entry point",
            inputs = "None",
            outputs = "Out",
            purpose = "The starting entry point for every scene graph.",
            description = "When a scene is triggered from the Global canvas, execution immediately starts at this node. It fires its Out port to begin the scene's internal flow.\n\nEvery scene must contain exactly one BEGIN_SCENE node.",
            parameters = listOf("Scene entry point indicator"),
            proTip = "Connect this node directly to your opening dialogue, setup action, or initial environment setup."
        ),
        GuideItem(
            id = "node_end_scene",
            folderId = "flow",
            icon = "🛑",
            title = "END_SCENE",
            subtitle = "Concludes the active scene and triggers Global OUT",
            inputs = "In",
            outputs = "None",
            purpose = "Terminates the current scene and transfers control to the next scene on the Global canvas.",
            description = "Receiving a signal at its In port cleanly finishes the scene, shuts down any active local processes/timers, and fires the outer OUT port of the scene block on the Global canvas.",
            parameters = listOf("Scene finish point indicator"),
            proTip = "You can place multiple END_SCENE nodes if your scene has different endings (e.g. Victory vs Defeat)."
        ),
        GuideItem(
            id = "node_construction",
            folderId = "flow",
            icon = "🏗️",
            title = "CONSTRUCTION",
            subtitle = "Reusable sub-graph container",
            inputs = "In",
            outputs = "Out",
            purpose = "Encapsulates complex logic chains into a clean, compact sub-canvas.",
            description = "Keeps your canvas tidy by nesting multi-node sequences into a single block. Double-click or click '🔍 Edit Internal' in the inspector to open its internal canvas. Inside, flow begins at BEGIN_CONSTRUCTION and exits through END_CONSTRUCTION.",
            parameters = listOf("Construction Title", "Internal sub-graph canvas"),
            proTip = "Great for complex mechanics like puzzle logic or reward distributions that you want to keep organized."
        ),
        GuideItem(
            id = "node_construction_ports",
            folderId = "flow",
            icon = "🚀",
            title = "BEGIN & END CONSTRUCTION",
            subtitle = "Sub-graph internal entry and exit nodes",
            inputs = "BEGIN: None | END: In",
            outputs = "BEGIN: Out | END: None",
            purpose = "Define the start and finish boundaries inside a Construction sub-graph.",
            description = "• BEGIN_CONSTRUCTION: Starts internal construction flow. Allows configuring Construction Name, Build Speed Mode (INSTANT, ANIMATED_LAYER_BY_LAYER, TIMED_STEP), Step Delay (Ticks), and Timeout Threshold (Ticks).\n" +
                "• END_CONSTRUCTION: Finishes construction flow. Options: Finalize Tags (YES/NO) and Play Completion Sound (with sound picker). Releases the outer Construction's Out port.",
            parameters = listOf(
                "Build Speed Mode: INSTANT, ANIMATED_LAYER_BY_LAYER, TIMED_STEP",
                "Delay Between Steps (Ticks)",
                "Timeout Threshold (Ticks)",
                "Finalize Tags & Completion Sound"
            ),
            proTip = "Use ANIMATED_LAYER_BY_LAYER with a step delay to make structures appear progressively block by block!"
        ),
        GuideItem(
            id = "node_gate",
            folderId = "flow",
            icon = "⚡",
            title = "GATE",
            subtitle = "Synchronizer AND-Gate (Waits for all inputs)",
            inputs = "2 to 5 IN ports (IN 1, IN 2, ...)",
            outputs = "OUT",
            purpose = "Synchronizes multiple parallel branches, firing only when ALL inputs arrive.",
            description = "Configurable between 2 and 5 input ports in the inspector. When signals arrive on all configured ports (regardless of order), the Gate fires its OUT port and resets its memory.",
            parameters = listOf("Inputs Count: 2 to 5 ports (configurable in inspector)"),
            proTip = "Essential for objectives requiring multiple tasks (e.g. 'Defeat 3 Trainers AND talk to the Professor before opening the gate')."
        ),
        GuideItem(
            id = "node_links",
            folderId = "flow",
            icon = "📡",
            title = "LINK_SEND & LINK_RECEIVE",
            subtitle = "Wireless signal transmitter and receiver",
            inputs = "LINK_SEND: In | LINK_RECEIVE: None",
            outputs = "LINK_SEND: None | LINK_RECEIVE: Out",
            purpose = "Transmits signals wirelessly without cluttering the canvas with long overlapping wires.",
            description = "Both nodes share a matching 'Channel Tag' string. When LINK_SEND receives an In signal, all LINK_RECEIVE nodes with the same channel tag immediately fire their Out ports.",
            parameters = listOf("Channel Tag: Case-insensitive identifier string"),
            proTip = "Use wireless links to loop back to dialogue hub nodes without stretching messy wires across the screen!"
        ),
        GuideItem(
            id = "node_loop",
            folderId = "flow",
            icon = "🔄",
            title = "LOOP",
            subtitle = "Repeater by count or continuous interval",
            inputs = "In (Start), Stop (Halt)",
            outputs = "Cycle (Each step), Done (Finished)",
            purpose = "Repeats an action chain multiple times or at timed intervals.",
            description = "• Operation Modes:\n" +
                "  - Count: Iterates N times (configured via 'Repetitions (Qty)'). Fires Cycle on each step, then fires Done upon completion.\n" +
                "  - Time: Repeats continuously every X seconds (configured via 'Interval (Sec)') until receiving a signal on its Stop port.\n\nRuns fully asynchronously without blocking the game engine.",
            parameters = listOf(
                "Mode: Count or Time",
                "Repetitions (Qty): Number of iterations",
                "Interval (Sec): Delay between cycles"
            ),
            proTip = "Connect a loop to a Variable Set (Add +1) to build a countdown timer, wave spawner, or recurring ambient check!"
        ),
        GuideItem(
            id = "node_comment",
            folderId = "flow",
            icon = "📝",
            title = "COMMENT (Note)",
            subtitle = "Yellow Post-It sticky note for canvas notes",
            inputs = "None",
            outputs = "None",
            purpose = "Visual note card for leaving notes, section titles, or reminders on the canvas.",
            description = "Rendered in classic yellow Post-It style (fixed yellow color theme). Features an editable title, note text (up to 300 characters), and an interactive resize drag handle in the bottom-right corner. Has no ports and zero performance cost during gameplay.",
            parameters = listOf("Note Title", "Note / Comment text (up to 300 characters)", "Resizable width and height via drag handle"),
            proTip = "Use notes above scene clusters to describe quest objectives or document variable requirements for your story!"
        ),

        // ==========================================
        // FOLDER 3: BLOCKS - LOGIC & CONTROL
        // ==========================================
        GuideItem(
            id = "node_trigger",
            folderId = "logic",
            icon = "🎯",
            title = "TRIGGER",
            subtitle = "World, player, time & Pokémon event listener",
            inputs = "In (Optional manual trigger)",
            outputs = "Out",
            purpose = "Detects world events or player actions to initiate story flow.",
            description = "Contains extensive built-in triggers across 6 categories:\n" +
                "• Story: Story Started, Story Ended, Quest Completed, Event Executed, Variable Value Check.\n" +
                "• Time: Time Elapsed, Time of Day (0=Dawn, 6000=Noon, 18000=Midnight), Days Passed, Day/Night Check.\n" +
                "• Player: EXP Level, Coordinates Reached (X, Y, Z radius), Biome, Held Item, Inventory Has Item, Item Removed, Item Quantity.\n" +
                "• Pokémon: Talk to Pokémon, Interact with Entity/Pokémon, Catch Pokémon, Highest Level in Party, Pokémon in Party, Friendship (0-255).\n" +
                "• Combat: Battle Start, Battle Victory, Battle Defeat, Entity Death, Entity Took Damage.\n" +
                "• World: Weather Check (Clear, Rain, Thunder), Block Interacted, Block Placed, Entity Spawned.",
            parameters = listOf(
                "Trigger Subtype: Picked from Trigger Registry",
                "Coordinates & Radius (for proximity triggers)",
                "Species, Forms, Levels & Tags (for Pokémon triggers)"
            ),
            proTip = "Use 'Coordinates Reached' with a 4-block radius around an NPC to automatically trigger a cutscene when the player walks up!"
        ),
        GuideItem(
            id = "node_timer",
            folderId = "logic",
            icon = "⏱️",
            title = "TIMER",
            subtitle = "Asynchronous delay timer",
            inputs = "In (Starts timer)",
            outputs = "Out (Fires after delay)",
            purpose = "Pauses execution for a configured duration in seconds.",
            description = "When triggered at In, counts down the configured 'Wait (Seconds)' duration asynchronously. Once elapsed, it fires Out. If the scene terminates while a timer is ticking, it automatically cancels safely.",
            parameters = listOf("Wait (Seconds): Countdown duration"),
            proTip = "Use a 2-second timer between dramatic dialogues to give players time to read and absorb plot revelations!"
        ),
        GuideItem(
            id = "node_condition",
            folderId = "logic",
            icon = "🔀",
            title = "CONDITION_NODE",
            subtitle = "Multi-branching conditional evaluator (IF / ELSE IF / ELSE)",
            inputs = "In",
            outputs = "IF, ELSE IF 1..N (Dynamic), ELSE (Optional)",
            purpose = "Branches story flow based on variables, comparisons, or list queries.",
            description = "• Branch 0 (IF): Compares selected variable against a target value using an operator.\n" +
                "• Dynamic ELSE IF: Add any number of additional ELSE IF branches in the inspector. Each creates its own dedicated output port (ELSE IF 1, ELSE IF 2, etc.)!\n" +
                "• Fallback (ELSE): Optional fallback port fired if all IF and ELSE IF checks evaluate to false.\n" +
                "• Operators:\n" +
                "  - Standard: ==, !=, >, <, >=, <=\n" +
                "  - LIST Variables: CONTAINS, SIZE, IS_EMPTY, GET_INDEX",
            parameters = listOf(
                "Variable picker for each branch",
                "Comparison Operator",
                "Target Value / Index",
                "Dynamic ELSE IF count & ELSE toggle"
            ),
            proTip = "Add multiple ELSE IF branches to cleanly handle player choices or starter Pokémon selections inside a single compact block!"
        ),
        GuideItem(
            id = "node_var_set",
            folderId = "logic",
            icon = "💾",
            title = "VARIABLE_SET",
            subtitle = "Assign or modify variable values",
            inputs = "In",
            outputs = "Out",
            purpose = "Modifies variable values during story execution.",
            description = "Select target variable and operation:\n" +
                "• NUMBER: = (Set), + (Add), - (Subtract), * (Multiply)\n" +
                "• BOOLEAN: = (Set), NOT (Invert/Toggle boolean)\n" +
                "• STRING: = (Set), + (Append text)\n" +
                "• LIST: ADD (Append item), REMOVE (Delete item), REMOVE_AT (Delete by numeric index), CLEAR (Empty list), SET (Set comma-separated items)",
            parameters = listOf(
                "Variable Picker",
                "Operation: Type-specific math or list operation",
                "Target Value"
            ),
            proTip = "Use operation '+' with value '1' on a NUMBER variable each time a player completes a task to build counter goals!"
        ),
        GuideItem(
            id = "node_var_get",
            folderId = "logic",
            icon = "🔍",
            title = "VARIABLE_GET",
            subtitle = "Read variable state & reactive ON_CHANGED signal",
            inputs = "None (or signal)",
            outputs = "Val, On Changed (Optional)",
            purpose = "Inspects variable values and provides reactive signals when variables update.",
            description = "• Variable Read: Inspects the value, type, and scope of any global or local variable.\n" +
                "• Reactive Signal (⚡ ON_CHANGED): Toggle 'ON_CHANGED: YES' in the inspector to add an 'On Changed' output port! This port fires automatically whenever the watched variable changes value anywhere in the storypack.\n" +
                "• List Queries: If inspecting a LIST variable, allows querying CONTAINS, SIZE, IS_EMPTY, or GET_INDEX.",
            parameters = listOf(
                "Variable Picker",
                "Reactive ON_CHANGED signal toggle",
                "List Query options (for LIST variables)"
            ),
            proTip = "Enable 'ON_CHANGED: YES' to build reactive HUD updates or trigger instant story reactions whenever a quest counter increments!"
        ),
        GuideItem(
            id = "node_checkpoints",
            folderId = "logic",
            icon = "🚩",
            title = "SAVE_STATE & LOAD_STATE",
            subtitle = "Create and restore story checkpoints",
            inputs = "SAVE: In | LOAD: In",
            outputs = "SAVE: Success, Error | LOAD: Success, Not Found, Error",
            purpose = "Saves and restores story progression, active nodes, and variables.",
            description = "• SAVE_STATE_NODE: Saves current story variables, visited nodes, and world states into a named profile slot on disk.\n" +
                "  - Parameters: Profile ID / Slot, Scope (PLAYER vs GLOBAL), Modules (ALL or custom list).\n" +
                "• LOAD_STATE_NODE: Restores story state and variables from a profile slot.\n" +
                "  - Parameters: Profile ID / Slot, Scope, Merge Mode (OVERWRITE vs SOFT_MERGE), Jump Target Node ID (node to resume execution at), Grace Period (ticks), Clean Story Tag (removes temporary story entities).",
            parameters = listOf(
                "Profile ID / Slot identifier",
                "Scope: PLAYER or GLOBAL",
                "Merge Mode: OVERWRITE or SOFT_MERGE",
                "Jump Target Node ID & Grace Period Ticks"
            ),
            proTip = "Place a SAVE_STATE node right before dangerous gym battles or puzzles so players can retry without restarting the entire chapter!"
        ),

        // ==========================================
        // FOLDER 4: BLOCKS - WORLD & INTERACTION
        // ==========================================
        GuideItem(
            id = "node_dialogue",
            folderId = "world",
            icon = "💬",
            title = "DIALOGUE",
            subtitle = "NPC/Pokémon speech, AI generation & 3D chat bubbles",
            inputs = "In",
            outputs = "Out (plus Fallback port if AI mode is active)",
            purpose = "Displays spoken dialogue, titles, actionbar text, or AI-generated speech.",
            description = "• Generation Mode:\n" +
                "  - Fixed Text: Direct dialogue text (up to 500 characters). Supports color codes (§a, §e, etc.) and <var:name> tokens.\n" +
                "  - AI Generated: AI instruction prompt (up to 2000 characters) with dedicated '🤖 AI Settings...' modal. Adds an extra 'Fallback' output port that fires if the AI request fails!\n" +
                "• Speech System:\n" +
                "  - Standard: Display as Chat, Title (with Subtitle, Hex Color, Fade In, Stay, Fade Out), or Actionbar.\n" +
                "  - CobbleBrain: Speaker Type (1st Active Party, Party Slot 1-6, Random Party, Nearest Wild, By Species/Name, Target Mob/NPC Tag, Custom Name), Name Format ([Name] Message vs Message Only), Emotion/Voice Pitch (Neutral, Happy, Sad/Angry, Excited, Custom Pitch 0.5-2.0), 3D Chat Bubble (with tick duration), Play Cry, Look at Player, Jump Effect, and Show in Chat.",
            parameters = listOf(
                "Generation Mode: Fixed Text vs AI Generated",
                "Speech System: Standard (Chat/Title/Bar) vs CobbleBrain",
                "Speaker Type, Display Name & Emotion/Pitch",
                "3D Chat Bubble, Cry, Look at Player & Jump toggles"
            ),
            proTip = "In AI Generated mode, always connect the 'Fallback' port to a backup Fixed Text dialogue so your story proceeds smoothly even if offline!"
        ),
        GuideItem(
            id = "node_action",
            folderId = "world",
            icon = "⚡",
            title = "ACTION",
            subtitle = "World modifications, entity interactions & effects",
            inputs = "In",
            outputs = "Out",
            purpose = "Executes physical world actions, spawns, teleports, and entity behaviors.",
            description = "Features extensive action subtypes organized across 8 categories:\n" +
                "• Map: Spawn Structure, Teleport (coordinates, safe position, snap to ground), Change Weather (Clear, Rain, Thunder), Set Time of Day.\n" +
                "• World: Place Block, Modify Block Properties.\n" +
                "• Entities: Spawn Entity, Kill Entity, Entity Properties (health, armor, speed, custom name, noAi), Apply Entity Effect, Area Effect, Look At / Focus Entity, Move / Pathfind Entity (walk, sprint, sneak), Entity Animation, Set Entity Texture, Manage Story Tag.\n" +
                "• Pokémon: Spawn Cobblemon (species, level, shiny, moves), Give Pokémon, Modify Pokémon (EXP, level, friendship, heal), CobbleBrain Personality, Party Effect.\n" +
                "• Player: Kill Player, Damage Player, Give Item, Remove Item, Player Effect.\n" +
                "• Items: Drop Item on Ground.\n" +
                "• Interface: Chat Message, Show Title Screen, Screen Tint / Fade.\n" +
                "• Effects: Spawn Particles, Play Sound Effect, Play Background Music.",
            parameters = listOf(
                "Action Subtype: Picked from Action Registry",
                "Target Coordinates, Selectors & Story Tags",
                "Custom parameters per action type"
            ),
            proTip = "Use 'Move / Pathfind Entity' with 'lockPositionOnArrival = true' to guide NPCs smoothly along cinematic cutscene routes!"
        ),
        GuideItem(
            id = "node_command",
            folderId = "world",
            icon = "⌨️",
            title = "COMMAND_NODE",
            subtitle = "Server & player console command execution",
            inputs = "In",
            outputs = "Out",
            purpose = "Executes Minecraft console or player commands with token support.",
            description = "• Execution Source:\n" +
                "  - 🖥️ Server (Level 2): Runs commands elevated as server console, allowing game modifications without requiring player OP permissions.\n" +
                "  - 👤 Local Player: Runs commands directly as the local player.\n" +
                "• Silent Mode (🔇 Silent: YES/NO): Hides or shows command execution feedback in the chat.\n" +
                "• Multi-line Commands: Enter multiple commands (1 per line, up to 1000 characters).\n" +
                "• Dynamic Tokens: Supports {player} (player username), {x}, {y}, {z}, and {variable_name}.\n" +
                "• Security: Automatically checks and blocks dangerous unauthorized admin commands.",
            parameters = listOf(
                "Execution Source: Server (Level 2) or Local Player",
                "Silent Mode toggle",
                "Commands (1 per line)",
                "Tokens: {player}, {x}, {y}, {z}, {var_name}"
            ),
            proTip = "Use Server mode to safely execute commands like '/give {player} cobblemon:rare_candy' on multiplayer servers without granting OP!"
        ),
        GuideItem(
            id = "node_texture",
            folderId = "world",
            icon = "🎨",
            title = "TEXTURE",
            subtitle = "Change entity textures & custom skins dynamically",
            inputs = "In",
            outputs = "Out",
            purpose = "Swaps entity textures, Pokémon skins, or NPC models in real-time.",
            description = "• Target Entity Type:\n" +
                "  - 🐾 Cobblemon: Specify party slot (1 to 6).\n" +
                "  - 👤 NPC / Mob: Target entity matching an Entity Story Tag.\n" +
                "• Texture Action Mode:\n" +
                "  - Apply Texture: Select custom .png skin from story assets.\n" +
                "  - Reset Default: Restores the entity's original default texture.\n" +
                "• Texture Selector: Click '📁 Browse Story Textures' to open the texture browser with instant search, file sizes, and directory locations.",
            parameters = listOf(
                "Target Entity: Cobblemon (Party Slot 1-6) or NPC/Mob (Story Tag)",
                "Action Mode: Apply Texture or Reset Default",
                "Selected Texture (.png) from storypack assets"
            ),
            proTip = "Drop PNG skin files directly into 'cobblebrain/storypacks/<story>/assets/textures/' to browse and apply them with one click!"
        ),
        GuideItem(
            id = "node_key_input",
            folderId = "world",
            icon = "🎮",
            title = "KEY_INPUT",
            subtitle = "Quick Time Events (QTE) & interactive key prompts",
            inputs = "In (Sequential Flow mode only)",
            outputs = "Out, Out Timeout (or Out Pulse, Out Release in Stream mode)",
            purpose = "Prompts the player to press, hold, or mash a key within a time limit.",
            description = "• Activation Mode:\n" +
                "  - ⚡ Standalone (No IN): Listens passively for the key press.\n" +
                "  - 🔗 Sequential Flow (With IN): Activates only when receiving a signal on its In port.\n" +
                "• Capture Modes:\n" +
                "  - PRESS: Instant click.\n" +
                "  - HOLD_ONE_SHOT: Must hold key for duration.\n" +
                "  - HOLD_STREAM: Continuous stream (outputs: Out Pulse, Out Release, Out Timeout).\n" +
                "  - RELEASE: Fires when releasing the key.\n" +
                "  - MASH: Requires pressing the key repeatedly.\n" +
                "• Target Key: Supports keyboard keys and mouse buttons (e.g. F, SPACE, E, LMB, RMB).",
            parameters = listOf(
                "Activation Mode: Standalone vs Sequential Flow",
                "Capture Mode: PRESS, HOLD, STREAM, RELEASE, MASH",
                "Target Key / Mouse button",
                "Timeout Duration (ticks)"
            ),
            proTip = "Use MASH mode during action scenes (e.g. 'Press SPACE repeatedly to lift the gate!') to create exciting arcade mini-games!"
        ),
        GuideItem(
            id = "node_quest",
            folderId = "world",
            icon = "📜",
            title = "QUEST (Mission)",
            subtitle = "Track objectives, progress & quest completion",
            inputs = "In",
            outputs = "Success (Completed), Fail (Failed), Progress (Step update)",
            purpose = "Manages active quest objectives, target counts, and HUD trackers.",
            description = "• Quest Title: Custom title displayed in player quest logs.\n" +
                "• Objective Trigger: Select the trigger event from Trigger Registry (e.g. POKEMON_CATCH, INTERACT_POKEMON, TALK_TO_POKEMON, BATTLE_VICTORY, ENTITY_DIED).\n" +
                "• Target Species & Story Tag: Filter by specific Pokémon species or entity tag.\n" +
                "• Target Count: Required quantity to complete (e.g. catch 3, defeat 5).\n" +
                "• Time Limit: Maximum seconds to complete (0 = infinite).\n" +
                "• Fail on Death (YES/NO): Fails quest if player dies before finishing.\n" +
                "• Show HUD (YES/NO): Displays on-screen progress tracker widget.\n" +
                "• Outputs:\n" +
                "  - Success: Fires when target count is achieved.\n" +
                "  - Fail: Fires if time limit expires or player dies (if enabled).\n" +
                "  - Progress: Fires on each step increment before completion.",
            parameters = listOf(
                "Quest Title",
                "Objective Trigger (from Trigger Registry)",
                "Target Species & Required Story Tag",
                "Target Count & Time Limit (seconds)",
                "Fail on Death & Show HUD toggles"
            ),
            proTip = "Connect the 'Progress' output port to an Action node playing a pickup sound so players get audio feedback on every objective step!"
        ),
        GuideItem(
            id = "node_audio",
            folderId = "world",
            icon = "🎵",
            title = "AUDIO",
            subtitle = "Sound effects, background music & 3D positional audio",
            inputs = "In",
            outputs = "Out",
            purpose = "Plays 3D sound effects, ambient cues, or background music.",
            description = "• Audio Modes:\n" +
                "  - PLAY_SOUND_EFFECT: Plays one-shot sound effect.\n" +
                "  - PLAY_BACKGROUND_MUSIC: Plays looping or one-time background music.\n" +
                "  - STOP_ALL_MUSIC: Immediately stops currently playing background music.\n" +
                "• Audio Catalog: Click '🎵 Audio' to open the built-in sound resource browser.\n" +
                "• Volume (0.0 - 2.0) and Pitch (0.5 - 2.0) controls.\n" +
                "• Loop (YES/NO) toggle.\n" +
                "• Spatial Mode: 2D Global (heard everywhere equally) vs 3D Positional (emanates from X, Y, Z coordinates with customizable block radius).",
            parameters = listOf(
                "Audio Mode: Sound Effect, Background Music, Stop Music",
                "Audio Resource ID (via sound picker)",
                "Volume (0.0 - 2.0) and Pitch (0.5 - 2.0)",
                "Loop toggle",
                "Spatial Mode: 2D Global vs 3D Positional (Coordinates & Radius)"
            ),
            proTip = "Use 3D Positional audio with a radius of 12 blocks to make waterfalls, mysterious caves, or legendary Pokémon cries feel real!"
        )
    )

    private var selectedItemId: String = "top_bar"
    private var hoveredItemId: String? = null
    private var hoveredFolderId: String? = null

    init {
        searchBox = EditBox(font, sidebarX + 4, sidebarY + 4, sidebarW - 8, 16, Component.literal("Search Guide"))
        searchBox.setMaxLength(50)
        searchBox.setHint(Component.literal("§8🔍 Search guide..."))
        searchBox.setEditable(true)
        searchBox.active = true
        searchBox.isFocused = false
        searchBox.setResponder {
            sidebarScroll = 0f
            val query = it.trim().lowercase()
            if (query.isNotBlank()) {
                val match = items.firstOrNull { item ->
                    item.title.lowercase().contains(query) ||
                    item.subtitle.lowercase().contains(query) ||
                    item.purpose.lowercase().contains(query)
                }
                if (match != null) {
                    selectedItemId = match.id
                    contentScroll = 0f
                }
            }
        }
    }

    private fun getFilteredItems(): List<GuideItem> {
        val q = searchBox.value.trim().lowercase()
        if (q.isBlank()) return items
        return items.filter {
            it.title.lowercase().contains(q) ||
            it.subtitle.lowercase().contains(q) ||
            it.purpose.lowercase().contains(q) ||
            it.description.lowercase().contains(q) ||
            (it.inputs?.lowercase()?.contains(q) == true) ||
            (it.outputs?.lowercase()?.contains(q) == true)
        }
    }

    fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Translucent background
        guiGraphics.fill(0, 0, screenW, screenH, 0xDD0A0F1D.toInt())

        // Modal Frame
        guiGraphics.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xF00F172A.toInt())
        guiGraphics.fill(modalX, modalY, modalX + modalW, modalY + 22, 0xFF1E293B.toInt())
        guiGraphics.fill(modalX, modalY, modalX + modalW, modalY + 1, 0xFF0284C7.toInt())
        guiGraphics.fill(modalX, modalY + modalH - 1, modalX + modalW, modalY + modalH, 0xFF0284C7.toInt())
        guiGraphics.fill(modalX, modalY, modalX + 1, modalY + modalH, 0xFF0284C7.toInt())
        guiGraphics.fill(modalX + modalW - 1, modalY, modalX + modalW, modalY + modalH, 0xFF0284C7.toInt())

        // Header Title
        guiGraphics.drawString(font, "📖 CobbleBrain Story Creator Guide", modalX + 10, modalY + 7, 0xFF38BDF8.toInt(), true)

        // Close Button (✖)
        val closeX = modalX + modalW - 20
        val closeY = modalY + 5
        val isCloseHover = mouseX >= closeX && mouseX <= closeX + 14 && mouseY >= closeY && mouseY <= closeY + 12
        guiGraphics.fill(closeX, closeY, closeX + 14, closeY + 12, if (isCloseHover) 0xFFDC2626.toInt() else 0xFF334155.toInt())
        guiGraphics.drawString(font, "✖", closeX + 4, closeY + 2, 0xFFFFFFFF.toInt(), false)

        // Render Left Sidebar (Folder Navigation & Search)
        renderSidebar(guiGraphics, mouseX, mouseY, partialTick)

        // Divider
        guiGraphics.fill(sidebarX + sidebarW + 3, sidebarY, sidebarX + sidebarW + 4, sidebarY + sidebarH, 0x4438BDF8)

        // Render Right Content Panel
        renderContent(guiGraphics, mouseX, mouseY)
    }

    private fun renderSidebar(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Search bar
        searchBox.render(guiGraphics, mouseX, mouseY, partialTick)

        val listY = sidebarY + 24
        val listH = sidebarH - 24
        guiGraphics.enableScissor(sidebarX, listY, sidebarX + sidebarW, listY + listH)

        val isSearching = searchBox.value.trim().isNotBlank()
        val filtered = getFilteredItems()
        var curY = listY + 2 - sidebarScroll.toInt()

        hoveredItemId = null
        hoveredFolderId = null

        folders.forEach { folder ->
            val folderItems = filtered.filter { it.folderId == folder.id }
            if (folderItems.isNotEmpty() || !isSearching) {
                // Folder Header
                val fH = 16
                val isFHover = mouseX >= sidebarX && mouseX <= sidebarX + sidebarW - 2 && mouseY >= curY && mouseY <= curY + fH
                if (isFHover) hoveredFolderId = folder.id

                val fBg = if (isFHover) 0x3338BDF8 else 0x15000000
                guiGraphics.fill(sidebarX, curY, sidebarX + sidebarW, curY + fH, fBg)

                val arrow = if (folder.isExpanded || isSearching) "▾" else "▸"
                val fTitle = "$arrow ${folder.icon} ${folder.name}"
                guiGraphics.drawString(font, font.plainSubstrByWidth(fTitle, sidebarW - 12), sidebarX + 4, curY + 4, 0xFFE2E8F0.toInt(), true)
                curY += fH + 2

                // Folder Items (if expanded or searching)
                if (folder.isExpanded || isSearching) {
                    folderItems.forEach { item ->
                        val itemH = 18
                        val isItemHover = mouseX >= sidebarX + 6 && mouseX <= sidebarX + sidebarW - 2 && mouseY >= curY && mouseY <= curY + itemH
                        val isSelected = item.id == selectedItemId
                        if (isItemHover) hoveredItemId = item.id

                        val iBg = when {
                            isSelected -> 0x660284C7
                            isItemHover -> 0x331E293B
                            else -> 0x00000000
                        }
                        if (iBg != 0) {
                            guiGraphics.fill(sidebarX + 6, curY, sidebarX + sidebarW, curY + itemH, iBg)
                        }
                        if (isSelected) {
                            guiGraphics.fill(sidebarX + 6, curY, sidebarX + 8, curY + itemH, 0xFF38BDF8.toInt())
                        }

                        val itemLabel = "${item.icon} ${item.title}"
                        val labelCol = if (isSelected) 0xFF38BDF8.toInt() else if (isItemHover) 0xFFFFFFFF.toInt() else 0xFF94A3B8.toInt()
                        guiGraphics.drawString(font, font.plainSubstrByWidth(itemLabel, sidebarW - 18), sidebarX + 12, curY + 5, labelCol, false)
                        curY += itemH + 1
                    }
                }
            }
        }

        guiGraphics.disableScissor()

        // Sidebar Scrollbar
        lastSidebarTotalH = (curY + sidebarScroll.toInt() - listY).toFloat()
        val maxSidebarScroll = (lastSidebarTotalH - listH).coerceAtLeast(0f)
        if (maxSidebarScroll > 0) {
            val sbX = sidebarX + sidebarW - 4
            val sbW = 3
            val scrollRatio = listH.toFloat() / lastSidebarTotalH
            val thumbH = (listH * scrollRatio).toInt().coerceAtLeast(14)
            val thumbY = listY + ((sidebarScroll / maxSidebarScroll) * (listH - thumbH)).toInt()

            val isHover = mouseX >= sbX - 2 && mouseX <= sbX + sbW + 2 && mouseY >= listY && mouseY <= listY + listH
            val thumbCol = if (isDraggingSidebarScroll) 0xFF38BDF8.toInt() else if (isHover) 0xFF00E5FF.toInt() else 0xFF0284C7.toInt()

            guiGraphics.fill(sbX, listY, sbX + sbW, listY + listH, 0x550F172A)
            guiGraphics.fill(sbX, thumbY, sbX + sbW, thumbY + thumbH, thumbCol)
        }
    }

    private fun renderContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val item = items.firstOrNull { it.id == selectedItemId } ?: items.first()

        guiGraphics.enableScissor(contentX, contentY, contentX + contentW, contentY + contentH)

        var curY = contentY + 2 - contentScroll.toInt()
        val textW = contentW - 16

        // 1. Header Card
        guiGraphics.fill(contentX, curY, contentX + contentW - 4, curY + 34, 0x551E293B)
        guiGraphics.fill(contentX, curY, contentX + 2, curY + 34, 0xFF38BDF8.toInt())
        guiGraphics.drawString(font, "${item.icon} ${item.title}", contentX + 8, curY + 6, 0xFF38BDF8.toInt(), true)
        guiGraphics.drawString(font, item.subtitle, contentX + 8, curY + 20, 0xFF94A3B8.toInt(), false)
        curY += 40

        // 2. Ports Card (If block node)
        if (item.inputs != null || item.outputs != null) {
            guiGraphics.fill(contentX, curY, contentX + contentW - 4, curY + 32, 0x440F172A)
            guiGraphics.drawString(font, "🔌 Ports & Connections:", contentX + 6, curY + 4, 0xFFFCD34D.toInt(), false)
            val inStr = "Inputs: §f${item.inputs ?: "None"}"
            val outStr = "Outputs: §f${item.outputs ?: "None"}"
            guiGraphics.drawString(font, font.plainSubstrByWidth(inStr, textW), contentX + 10, curY + 18, 0xFF34D399.toInt(), false)
            curY += 36
            guiGraphics.drawString(font, font.plainSubstrByWidth(outStr, textW), contentX + 10, curY, 0xFF60A5FA.toInt(), false)
            curY += 16
        }

        // 3. Purpose / Função
        guiGraphics.drawString(font, "🎯 Purpose / Função:", contentX + 4, curY, 0xFFFCD34D.toInt(), true)
        curY += 12
        val purposeLines = font.split(Component.literal(item.purpose), textW)
        for (line in purposeLines) {
            guiGraphics.drawString(font, line, contentX + 8, curY, 0xFFF1F5F9.toInt(), false)
            curY += font.lineHeight + 2
        }
        curY += 6

        // 4. Description / Como Funciona
        guiGraphics.drawString(font, "📖 How it Works / Detalhes:", contentX + 4, curY, 0xFFFCD34D.toInt(), true)
        curY += 12
        val descLines = font.split(Component.literal(item.description), textW)
        for (line in descLines) {
            guiGraphics.drawString(font, line, contentX + 8, curY, 0xFFCBD5E1.toInt(), false)
            curY += font.lineHeight + 2
        }
        curY += 6

        // 5. Parameters (if any)
        if (item.parameters.isNotEmpty()) {
            guiGraphics.drawString(font, "⚙️ Key Parameters / Opções:", contentX + 4, curY, 0xFFFCD34D.toInt(), true)
            curY += 12
            item.parameters.forEach { param ->
                val pLines = font.split(Component.literal("• $param"), textW)
                for (line in pLines) {
                    guiGraphics.drawString(font, line, contentX + 8, curY, 0xFF94A3B8.toInt(), false)
                    curY += font.lineHeight + 2
                }
            }
            curY += 6
        }

        // 6. Pro Tip Card (if any)
        if (item.proTip != null) {
            val tipW = contentW - 8
            val tipLines = font.split(Component.literal("💡 Pro Tip: ${item.proTip}"), tipW - 16)
            val cardH = tipLines.size * (font.lineHeight + 2) + 12

            guiGraphics.fill(contentX + 2, curY, contentX + 2 + tipW, curY + cardH, 0x440284C7)
            guiGraphics.fill(contentX + 2, curY, contentX + 4, curY + cardH, 0xFF0EA5E9.toInt())

            var tipY = curY + 6
            for (line in tipLines) {
                guiGraphics.drawString(font, line, contentX + 10, tipY, 0xFFE0F2FE.toInt(), false)
                tipY += font.lineHeight + 2
            }
            curY += cardH + 10
        }

        guiGraphics.disableScissor()

        // Content Scrollbar
        lastContentTotalH = (curY + contentScroll.toInt() - contentY).toFloat()
        val maxScroll = (lastContentTotalH - contentH).coerceAtLeast(0f)
        if (maxScroll > 0) {
            val sbW = 5
            val sbX = contentX + contentW - sbW - 1
            val scrollRatio = contentH.toFloat() / lastContentTotalH
            val thumbH = (contentH * scrollRatio).toInt().coerceAtLeast(15)
            val thumbY = contentY + ((contentScroll / maxScroll) * (contentH - thumbH)).toInt()

            val isHover = mouseX >= sbX - 2 && mouseX <= sbX + sbW + 2 && mouseY >= contentY && mouseY <= contentY + contentH
            val thumbCol = if (isDraggingContentScroll) 0xFF38BDF8.toInt() else if (isHover) 0xFF00E5FF.toInt() else 0xFF0284C7.toInt()

            guiGraphics.fill(sbX, contentY, sbX + sbW, contentY + contentH, 0x550F172A)
            guiGraphics.fill(sbX, thumbY, sbX + sbW, thumbY + thumbH, thumbCol)
        }
    }

    fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        if (isDraggingContentScroll) {
            val maxScroll = (lastContentTotalH - contentH).coerceAtLeast(0f)
            if (maxScroll > 0) {
                val scrollRatio = contentH.toFloat() / lastContentTotalH
                val thumbH = (contentH * scrollRatio).toInt().coerceAtLeast(15)
                val trackRange = contentH - thumbH
                if (trackRange > 0) {
                    val deltaY = mouseY - dragStartMouseY
                    val scrollDelta = (deltaY / trackRange).toFloat() * maxScroll
                    contentScroll = (dragStartContentScroll + scrollDelta).coerceIn(0f, maxScroll)
                    return true
                }
            }
        }
        if (isDraggingSidebarScroll) {
            val listH = sidebarH - 24
            val maxSidebarScroll = (lastSidebarTotalH - listH).coerceAtLeast(0f)
            if (maxSidebarScroll > 0) {
                val scrollRatio = listH.toFloat() / lastSidebarTotalH
                val thumbH = (listH * scrollRatio).toInt().coerceAtLeast(14)
                val trackRange = listH - thumbH
                if (trackRange > 0) {
                    val deltaY = mouseY - dragStartMouseY
                    val scrollDelta = (deltaY / trackRange).toFloat() * maxSidebarScroll
                    sidebarScroll = (dragStartSidebarScroll + scrollDelta).coerceIn(0f, maxSidebarScroll)
                    return true
                }
            }
        }
        return false
    }

    fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (isDraggingContentScroll || isDraggingSidebarScroll) {
            isDraggingContentScroll = false
            isDraggingSidebarScroll = false
            return true
        }
        return false
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != 0) return false

        // Check if outside modal bounds -> close
        if (mouseX < modalX || mouseX > modalX + modalW || mouseY < modalY || mouseY > modalY + modalH) {
            onClose()
            return true
        }

        // Close button
        val closeX = modalX + modalW - 20
        val closeY = modalY + 5
        if (mouseX >= closeX && mouseX <= closeX + 14 && mouseY >= closeY && mouseY <= closeY + 12) {
            onClose()
            return true
        }

        // Search box click
        val clickedSearch = searchBox.mouseClicked(mouseX, mouseY, button)
        searchBox.isFocused = clickedSearch
        if (clickedSearch) return true

        // Check Content Scrollbar Click
        val maxContentScroll = (lastContentTotalH - contentH).coerceAtLeast(0f)
        if (maxContentScroll > 0) {
            val sbW = 5
            val sbX = contentX + contentW - sbW - 1
            if (mouseX >= sbX - 4 && mouseX <= contentX + contentW && mouseY >= contentY && mouseY <= contentY + contentH) {
                val scrollRatio = contentH.toFloat() / lastContentTotalH
                val thumbH = (contentH * scrollRatio).toInt().coerceAtLeast(15)
                val thumbY = contentY + ((contentScroll / maxContentScroll) * (contentH - thumbH)).toInt()

                isDraggingContentScroll = true
                dragStartMouseY = mouseY
                if (mouseY >= thumbY && mouseY <= thumbY + thumbH) {
                    dragStartContentScroll = contentScroll
                } else {
                    val trackRange = contentH - thumbH
                    if (trackRange > 0) {
                        val clickOffset = ((mouseY - contentY - thumbH / 2.0) / trackRange).coerceIn(0.0, 1.0).toFloat()
                        contentScroll = clickOffset * maxContentScroll
                        dragStartContentScroll = contentScroll
                    }
                }
                return true
            }
        }

        // Check Sidebar Scrollbar Click
        val listY = sidebarY + 24
        val listH = sidebarH - 24
        val maxSidebarScroll = (lastSidebarTotalH - listH).coerceAtLeast(0f)
        if (maxSidebarScroll > 0) {
            val sbW = 4
            val sbX = sidebarX + sidebarW - sbW - 1
            if (mouseX >= sbX - 4 && mouseX <= sidebarX + sidebarW && mouseY >= listY && mouseY <= listY + listH) {
                val scrollRatio = listH.toFloat() / lastSidebarTotalH
                val thumbH = (listH * scrollRatio).toInt().coerceAtLeast(14)
                val thumbY = listY + ((sidebarScroll / maxSidebarScroll) * (listH - thumbH)).toInt()

                isDraggingSidebarScroll = true
                dragStartMouseY = mouseY
                if (mouseY >= thumbY && mouseY <= thumbY + thumbH) {
                    dragStartSidebarScroll = sidebarScroll
                } else {
                    val trackRange = listH - thumbH
                    if (trackRange > 0) {
                        val clickOffset = ((mouseY - listY - thumbH / 2.0) / trackRange).coerceIn(0.0, 1.0).toFloat()
                        sidebarScroll = clickOffset * maxSidebarScroll
                        dragStartSidebarScroll = sidebarScroll
                    }
                }
                return true
            }
        }

        // Check folder header clicks or item clicks in sidebar
        if (mouseX >= sidebarX && mouseX <= sidebarX + sidebarW && mouseY >= sidebarY && mouseY <= sidebarY + sidebarH) {
            if (hoveredFolderId != null) {
                val folder = folders.firstOrNull { it.id == hoveredFolderId }
                if (folder != null) {
                    folder.isExpanded = !folder.isExpanded
                    return true
                }
            }
            if (hoveredItemId != null) {
                selectedItemId = hoveredItemId!!
                contentScroll = 0f
                return true
            }
        }

        return true
    }

    fun mouseScrolled(mouseX: Double, mouseY: Double, scrollY: Double): Boolean {
        // Scroll sidebar if hovering over sidebar
        if (mouseX >= sidebarX && mouseX <= sidebarX + sidebarW && mouseY >= sidebarY && mouseY <= sidebarY + sidebarH) {
            sidebarScroll = (sidebarScroll - scrollY.toFloat() * 16f).coerceAtLeast(0f)
            return true
        }

        // Scroll content if hovering over content area
        if (mouseX >= contentX && mouseX <= contentX + contentW && mouseY >= contentY && mouseY <= contentY + contentH) {
            contentScroll = (contentScroll - scrollY.toFloat() * 20f).coerceAtLeast(0f)
            return true
        }

        return false
    }

    fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        return searchBox.charTyped(codePoint, modifiers)
    }

    fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == 256) {
            onClose()
            return true
        }
        if (searchBox.keyPressed(keyCode, scanCode, modifiers)) return true
        if (searchBox.isFocused && (keyCode == 259 || keyCode == 261)) {
            return true
        }
        return searchBox.isFocused
    }
}
