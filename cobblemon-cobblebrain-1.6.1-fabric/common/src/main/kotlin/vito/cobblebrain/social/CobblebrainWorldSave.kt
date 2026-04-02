package vito.cobblebrain.social

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.Filterable
import net.minecraft.world.entity.Mob
import net.minecraft.world.level.storage.LevelResource
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.WrittenBookContent

object MobBridge {
    var addGoal: ((Mob, Int, Goal) -> Unit)? = null
    var removeGoal: ((Mob, Goal) -> Unit)? = null

    var getGoals: ((Mob) -> List<Goal>)? = null
}

class FollowPlayerGoal(
    val pokemon: PokemonEntity,
    val player: ServerPlayer,
    val speed: Double,
    val startDistance: Float,
    val stopDistance: Float,
    val maxDistance: Double
) : Goal() {

    override fun canUse(): Boolean {
        return player.isAlive && pokemon.distanceTo(player) > startDistance
    }

    override fun canContinueToUse(): Boolean {
        return pokemon.distanceTo(player) > stopDistance && pokemon.isAlive
    }

    override fun start() {
        // nada aqui, só start do Goal
    }

    override fun tick() {
        val distance = pokemon.distanceTo(player)

        if (distance > maxDistance) {
            pokemon.teleportTo(player.x, player.y, player.z)
        } else {
            pokemon.navigation.moveTo(player, speed)
        }

        pokemon.lookAt(player, 30f, 30f)
    }
}

object CobblebrainWorldSave {
    data class Quest(
        val player: ServerPlayer,
        val type: String,
        var active: Boolean,
        val itemName: String? = null,
        val amount: Int? = null,
        val questSummary: String? = null
    )

    private lateinit var saveFile: File
    private val quests: MutableList<Quest> = mutableListOf()
    var data: JsonObject = JsonObject()

    // Agora precisa ser chamado quando o server iniciar
    fun init(server: MinecraftServer) {
        val dataDir = server.getWorldPath(LevelResource.ROOT).resolve("data").toFile()
        dataDir.mkdirs()

        saveFile = File(dataDir, "cobblebrainWorldSave.json")

        if (saveFile.exists()) {
            data = JsonParser.parseReader(FileReader(saveFile)).asJsonObject
        } else {
            data = JsonObject().apply {
                add("received_guide", JsonArray())
                add("karma", JsonObject())
                add("kill_count", JsonObject())
                add("quests", JsonObject().apply {
                    add("active", JsonArray())
                    add("completed", JsonArray())
                    add("abandoned", JsonArray())
                })
            }
            save()
        }
    }

    fun hasReceivedGuide(player: ServerPlayer): Boolean {
        val array = data.getAsJsonArray("received_guide")
        return array.any { it.asString == player.uuid.toString() }
    }
    fun markGuideReceived(player: ServerPlayer) {
        val array = data.getAsJsonArray("received_guide")
        array.add(player.uuid.toString())
        save()
    }

    fun save() {
        val file = saveFile
        FileWriter(file).use { writer ->
            GsonBuilder().setPrettyPrinting().create().toJson(data, writer)
        }
    }

    var battleSpecies = listOf(
        // Kanto (20)
        "Pidgey", "Rattata", "Zubat", "Geodude", "Oddish",
        "Caterpie", "Weedle", "Ekans", "Sandshrew", "Poliwag",
        "Machop", "Magnemite", "Gastly", "Cubone", "Tentacool",
        "Venonat", "Paras", "Diglett", "Doduo", "Meowth",
        // Johto (15)
        "Sentret", "Hoothoot", "Mareep", "Wooper", "Spinarak",
        "Ledyba", "Slugma", "Swinub", "Remoraid", "Phanpy",
        "Aipom", "Natu", "Sunkern", "Snubbull",
        // Hoenn (15)
        "Wurmple", "Zigzagoon", "Lotad", "Wingull", "Shroomish",
        "Electrike", "Gulpin", "Numel", "Spoink", "Meditite",
        "Makuhita", "Aron", "Trapinch", "Baltoy", "Swablu",
        // Sinnoh (15)
        "Bidoof", "Starly", "Kricketot", "Buizel", "Shellos",
        "Cherubi", "Combee", "Glameow", "Stunky", "Bronzor",
        "Croagunk", "Finneon", "Snover", "Hippopotas", "Pachirisu",
        // Unova (15)
        "Patrat", "Purrloin", "Blitzle", "Sewaddle", "Roggenrola",
        "Tympole", "Venipede", "Cottonee", "Petilil", "Sandile",
        "Darumaka", "Trubbish", "Foongus", "Joltik", "Ducklett",
        // Kalos (15)
        "Fletchling", "Scatterbug", "Bunnelby", "Skiddo", "Pancham",
        "Espurr", "Inkay", "Binacle", "Helioptile", "Pumpkaboo",
        "Swirlix", "Spritzee", "Honedge", "Clauncher", "Litleo",
        // Alola (10)
        "Pikipek", "Yungoos", "Grubbin", "Cutiefly", "Rockruff",
        "Fomantis", "Salandit", "Stufful", "Crabrawler", "Bounsweet",
        // Galar (5)
        "Skwovet", "Rookidee", "Nickit", "Chewtle", "Wooloo"
    )

    val followers = mutableMapOf<String, Triple<PokemonEntity, ServerPlayer, FollowPlayerGoal>>()

    private fun startFollowingPlayer(giver: PokemonEntity, player: ServerPlayer) {
        // Se já estiver seguindo, não adiciona outro
        if (followers.containsKey(giver.uuid.toString())) return

        giver.setPersistenceRequired()
        giver.isNoAi = false

        val goal = FollowPlayerGoal(giver, player, 0.5, 10f, 7f, 35.0)
        val addGoal = MobBridge.addGoal ?: return
        addGoal(giver, 1, goal)

        followers[giver.uuid.toString()] = Triple(giver, player, goal)
    }

    fun createBattleQuest(player: ServerPlayer, giver: PokemonEntity): Quest {
        val targetSpecies = battleSpecies.random()

        val questObj = JsonObject().apply {
            addProperty("ownerUuid", player.uuid.toString())
            addProperty("giverUuid", giver.uuid.toString())
            addProperty("type", "BATTLE")
            addProperty("targetSpecies", targetSpecies)
            addProperty("status", "IN_PROGRESS")
            addProperty("giverSpecies", giver.pokemon.species.name)
            addProperty("questSummary", "This is a Battle quest!")
            giver.pokemon.nickname?.string?.let { addProperty("giverNickname", it) }
        }

        data.getAsJsonObject("quests").getAsJsonArray("active").add(questObj)
        save()

        val quest = Quest(player, "BATTLE", true)
        quests.add(quest)

        val giverName = giver.pokemon.nickname?.string ?: giver.pokemon.species.resourceIdentifier.path
        println("IMPORTANT: $giverName asks you to defeat a $targetSpecies in battle!")

        // Ativa comportamento de seguir o jogador
        startFollowingPlayer(giver, player)

        return quest
    }

    fun createItemQuest(player: ServerPlayer, giver: PokemonEntity): Quest {
        val items = listOf("sweet_berries", "apple", "coal", "copper_ingot")
        val target = items.random()
        val amount = (1..10).random()

        val questObj = JsonObject().apply {
            addProperty("ownerUuid", player.uuid.toString())
            addProperty("giverUuid", giver.uuid.toString())
            addProperty("type", "ITEM")
            addProperty("target", target)
            addProperty("amount", amount)
            addProperty("status", "IN_PROGRESS")
            addProperty("giverSpecies", giver.pokemon.species.name)
            addProperty("questSummary", "This is a Item quest!")
            startFollowingPlayer(giver, player)
        }

        data.getAsJsonObject("quests").getAsJsonArray("active").add(questObj)
        save()

        val quest = Quest(player, "ITEM", true, target, amount)
        quests.add(quest)

        val giverName = giver.pokemon.nickname?.string ?: giver.pokemon.species.resourceIdentifier.path
        println("IMPORTANT: $giverName asks you to bring $amount $target(s)!")
        startFollowingPlayer(giver, player)
        return quest
    }

    fun createAdviceQuest(player: ServerPlayer, giver: PokemonEntity) {
        val questObj = JsonObject().apply {
            addProperty("ownerUuid", player.uuid.toString())
            addProperty("giverUuid", giver.uuid.toString())
            addProperty("type", "ADVICE")
            addProperty("status", "IN_PROGRESS")
            addProperty("giverSpecies", giver.pokemon.species.name)
            addProperty("questSummary", "This is a Advice quest!")
            giver.pokemon.nickname?.string?.let { addProperty("giverNickname", it) }
        }

        data.getAsJsonObject("quests").getAsJsonArray("active").add(questObj)
        save()

        val quest = Quest(player, "ADVICE", true)
        quests.add(quest)

        val giverName = giver.pokemon.nickname?.string ?: giver.pokemon.species.resourceIdentifier.path
        println("IMPORTANT: $giverName has started an ADVICE quest! It wants to talk to the player.")
        startFollowingPlayer(giver, player)
    }

    fun findQuest(giverUuid: String, type: String, status: String? = null): JsonObject? {
        val activeArray = data.getAsJsonObject("quests").getAsJsonArray("active")
        return activeArray.firstOrNull {
            val obj = it.asJsonObject
            obj.get("giverUuid").asString == giverUuid &&
                    obj.get("type").asString == type &&
                    (status == null || obj.get("status").asString == status)
        }?.asJsonObject
    }

    fun getActiveQuest(player: ServerPlayer): JsonObject? {
        val activeArray = data.getAsJsonObject("quests").getAsJsonArray("active")

        return activeArray
            .map { it.asJsonObject }
            .asReversed()
            .firstOrNull {
                it.get("status").asString == "IN_PROGRESS" &&
                        it.get("ownerUuid")?.asString == player.uuid.toString()
            }
    }

    fun getGiverNameFromQuest(quest: JsonObject): String {
        // Se tiver nickname, usa ele
        if (quest.has("giverNickname")) {
            val nickname = quest.get("giverNickname").asString
            if (nickname.isNotBlank()) return nickname
        }
        // Senão, usa a espécie
        if (quest.has("giverSpecies")) {
            val species = quest.get("giverSpecies").asString
            if (species.isNotBlank()) return species
        }
        // Último recurso: UUID
        return quest.get("giverUuid").asString
    }

    // Move uma quest de active para completed ou abandoned
    fun moveQuest(ownerUuid: String, giverUuid: String, type: String, newStatus: String) {
        println("[DEBUG] moveQuest called with giverUuid=$giverUuid, type=$type, newStatus=$newStatus")

        val questsObj = data.getAsJsonObject("quests")
        val activeArray = questsObj.getAsJsonArray("active")
        val completedArray = questsObj.getAsJsonArray("completed")
        val abandonedArray = questsObj.getAsJsonArray("abandoned")

        val questElement = activeArray.firstOrNull {
            val obj = it.asJsonObject
            obj.get("giverUuid").asString == giverUuid &&
                    obj.get("type").asString == type &&
                    obj.get("ownerUuid").asString == ownerUuid
        }

        if (questElement == null) {
            println("[DEBUG] No active quest found to move")
            return
        }

        val questObj = questElement.asJsonObject
        println("[DEBUG] Quest found: $questObj")

        questObj.addProperty("status", newStatus)

        // remove da lista de ativos
        activeArray.remove(questElement)
        println("[DEBUG] Quest removida de active")

        // adiciona na lista correta
        when (newStatus) {
            "COMPLETED" -> {
                completedArray.add(questObj)
                println("[DEBUG] Quest adicionada em completed")
            }

            "ABANDONED" -> {
                abandonedArray.add(questObj)
                println("[DEBUG] Quest adicionada em abandoned")
            }

            else -> println("[DEBUG] Status $newStatus não moveu para completed/abandoned")
        }

        save()
        println("[DEBUG] JSON saved after moveQuest")
        debugQuests()

        // Se o Pokémon estava seguindo, para de seguir
        followers[giverUuid]?.let { triple ->
            val pokemon = triple.first
            val goal = triple.third

            MobBridge.removeGoal?.invoke(pokemon, goal)

            pokemon.navigation.stop()
            followers.remove(giverUuid)

            println("[DEBUG] FollowGoal removed")
        }
    }

    fun adjustKarma(player: ServerPlayer, species: String, delta: Int) {
        println("[DEBUG] adjustKarma chamado para ${player.name.string} | espécie=$species | delta=$delta")
        val karmaRoot = data.getAsJsonObject("karma")
        val playerKey = player.uuid.toString()

        val playerObj = if (karmaRoot.has(playerKey)) {
            karmaRoot.getAsJsonObject(playerKey)
        } else {
            val newObj = JsonObject()
            karmaRoot.add(playerKey, newObj)
            newObj
        }

        val current = playerObj.get(species)?.asInt ?: 0
        val newValue = current + delta

        playerObj.addProperty(species, newValue)
        println("[DEBUG] Karma atualizado: $species = $newValue para ${player.name.string}")
        save()
    }

    // Debug: imprime estado atual das quests
    fun debugQuests() {
        val questsObj = data.getAsJsonObject("quests")
        println("Active quests: ${questsObj.getAsJsonArray("active")}")
        println("Completed quests: ${questsObj.getAsJsonArray("completed")}")
        println("Abandoned quests: ${questsObj.getAsJsonArray("abandoned")}")
        println("Karma: ${data.getAsJsonObject("karma")}")
    }

    fun adjustKillCount(player: ServerPlayer, species: String, delta: Int) {
        println("[DEBUG] adjustKillCount chamado para ${player.name.string} | espécie=$species | delta=$delta")
        val killRoot = data.getAsJsonObject("kill_count")
        val playerKey = player.uuid.toString()

        val playerObj = if (killRoot.has(playerKey)) {
            killRoot.getAsJsonObject(playerKey)
        } else {
            val newObj = JsonObject()
            killRoot.add(playerKey, newObj)
            newObj
        }

        val current = playerObj.get(species)?.asInt ?: 0
        val newValue = current + delta
        playerObj.addProperty(species, newValue)
        println("[DEBUG] Kill count atualizado: $species = $newValue para ${player.name.string}")
        save()
    }
    fun giveCobblebrainGuide(player: ServerPlayer) {
        val book = ItemStack(Items.WRITTEN_BOOK)

        fun clickablePage(text: String, page: Int): Component {
            return Component.literal(text)
                .withStyle(ChatFormatting.BLUE, ChatFormatting.UNDERLINE)
                .withStyle {
                    it.withClickEvent(
                        ClickEvent(ClickEvent.Action.CHANGE_PAGE, page.toString())
                    )
                }
        }

        val pages = listOf(
            Component.literal("")
                .append(Component.literal("COBBLEBRAIN MANUAL\n\n").withStyle(ChatFormatting.BOLD))
                .append("Select a section:\n\n")
                .append(clickablePage("Setup", 2)).append("\n")
                .append(clickablePage("Talk to Pokemon", 4)).append("\n")
                .append(clickablePage("Actions", 5)).append("\n")
                .append(clickablePage("Quests", 11)).append("\n")
                .append(clickablePage("Karma", 15)).append("\n")
                .append(clickablePage("Raids", 17)).append("\n")
                .append(clickablePage("Custom Settings", 18)).append("\n")
                .append(clickablePage("Developer’s Notes", 20)).append("\n"),

            // PAGE 2 - SETUP
            Component.literal("")
                .append(Component.literal("RECOMMENDED SETUP\n\n").withStyle(ChatFormatting.BOLD))
                .append("1. Create an account in ")
                .append(
                    Component.literal("Player2")
                        .withStyle(ChatFormatting.BLUE, ChatFormatting.UNDERLINE)
                        .withStyle {
                            it.withClickEvent(
                                ClickEvent(
                                    ClickEvent.Action.OPEN_URL,
                                    "https://player2.game/"
                                )
                            )
                        }
                )
                .append("\n2. Install the app and log in\n")
                .append("3. Choose a chat model inside the app\n\n")
                .append("Cheaper models = -power but +playtime\n\n")
                .append(
                    Component.literal("Youtube tutorial!")
                        .withStyle(ChatFormatting.RED, ChatFormatting.UNDERLINE)
                        .withStyle {
                            it.withClickEvent(
                                ClickEvent(
                                    ClickEvent.Action.OPEN_URL,
                                    "https://youtu.be/tPInNexUEmM"
                                )
                            )
                        }
                )
                .append("\n"),

            Component.literal("")
                .append(Component.literal("ALTERNATIVE SETUP\n\n").withStyle(ChatFormatting.BOLD))
                .append("You can use any API compatible with\n")
                .append("the OpenAI format.\n\n")
                .append("Examples:\n")
                .append("- OpenRouter\n")
                .append("- Google AI Studio\n\n")
                .append(
                    Component.literal("Youtube tutorial!")
                        .withStyle(ChatFormatting.RED, ChatFormatting.UNDERLINE)
                        .withStyle {
                            it.withClickEvent(
                                ClickEvent(
                                    ClickEvent.Action.OPEN_URL,
                                    "https://youtu.be/Th1ylIsnQlg"
                                )
                            )
                        }
                ),

            Component.literal("")
                .append(Component.literal("TALKING TO POKEMON\n\n").withStyle(ChatFormatting.BOLD))
                .append("You have two ways to communicate.\n\n")
                .append("Private Mode:\n")
                .append("Use /mpk to send a message.\n")
                .append("Only you will see your sent text.\n\n")
                .append("Chat Mode:\n")
                .append("Enable 'Listen to Chat'.\n")
                .append("Pokemon react to normal chat.\n")
                .append("Other players can see your message."),

            Component.literal("")
                .append(Component.literal("ACTIONS\n\n").withStyle(ChatFormatting.BOLD))
                .append("Pokémon can perform actions when you ask them.\n\n")
                .append("There are two types:\n")
                .append("1. Type-Based Actions\n")
                .append("2. General Actions\n\n")
                .append("Type-Based actions depend on\n")
                .append("the PRIMARY type only."),

            Component.literal("")
                .append(Component.literal("TYPE-BASED ACTIONS\n\n").withStyle(ChatFormatting.BOLD))
                .append("E.g: Chandelure is (Ghost/Fire)\n")
                .append("It can use Shift, but not Cook\n\n")
                .append("Fire - Cook\n")
                .append("Cooks food and smelts ores.\n")
                .append("5% chance to turn item into charcoal.\n\n"),

            Component.literal("")
                .append("Ghost - Shift\n")
                .append("Moves player to alternate dimension.\n")
                .append("You gain invisibility, speed and jump.\n")
                .append("But you suffer strong weakness.\n\n")
                .append("Steel - Repair\n")
                .append("Fix tools a bit. Cooldown of 5 minutes\n"),

            Component.literal("")
                .append("Grass - Grow\n")
                .append("Grows crops and tree saplings."),

            Component.literal("")
                .append(Component.literal("GENERAL ACTIONS\n\n").withStyle(ChatFormatting.BOLD))
                .append("Attack - Fight any nearby entities\n")
                .append("Except tagged and tamed mobs.\n\n")
                .append("Protect - Defend player from \n\n")
                .append("Eat - Consume dropped food"),

            Component.literal("")
                .append("Buff - Give positive effect to player\n\n")
                .append("Debuff - Apply negative effect to mobs\n\n")
                .append("Sit - Stay in place\n\n")
                .append("Idle - Cancel all actions"),

            Component.literal("")
                .append(Component.literal("QUESTS\n\n").withStyle(ChatFormatting.BOLD))
                .append("Wild Pokemon may generate quests\n")
                .append("during spontaneous dialogue.\n\n")
                .append("Default chance is 40%.\n")
                .append("You can change this in settings.\n\n")
                .append("There are currently 3 types of quests."),

            Component.literal("")
                .append(Component.literal("QUEST TYPES\n\n").withStyle(ChatFormatting.BOLD))
                .append("ITEM QUEST\n")
                .append("Bring specific items to the Pokemon.\n\n")
                .append("BATTLE QUEST\n")
                .append("Defeat a target in Pokemon battle.\n")
                .append("Killing outside battle does not count.\n\n"),

            Component.literal("")
                .append("ADVICE QUEST\n")
                .append("Give advice and make Pokemon happy.\n")
                .append("Multiple solutions are possible."),

            Component.literal("")
                .append(Component.literal("QUEST REWARDS\n\n").withStyle(ChatFormatting.BOLD))
                .append("Completing quests grants Karma.\n\n")
                .append("High Karma may give rewards such as:\n")
                .append("- Berries\n")
                .append("- EXP Candy\n")
                .append("- Rare items\n\n")
                .append("You can modify quest chance\n")
                .append("in 'wild quest chance' setting."),

            Component.literal("")
                .append(Component.literal("KARMA SYSTEM\n\n").withStyle(ChatFormatting.BOLD))
                .append("Karma represents how much\n")
                .append("a species respects you.\n\n")
                .append("Each species tracks you separately.\n\n")
                .append("To see your karma:\n")
                .append("/cobblebrain karma"),

            Component.literal("")
                .append("+Karma:\n")
                .append(" °Complete quests\n\n")
                .append("-Karma:\n")
                .append(" °Defeat or kill Pokémon\n")
                .append(" °Annoy Pokémon in quests\n\n")
                .append("High Karma ( > 2 ) may gives gifts.\n")
                .append("Low Karma ( < -7 ) may trigger raids."),

            Component.literal("")
                .append(Component.literal("RAID DETAILS\n\n").withStyle(ChatFormatting.BOLD))
                .append("Maximum difficulty at -30 Karma.\n\n")
                .append("Higher difficulty means:\n")
                .append("- More Pokemon spawn\n")
                .append("- Higher levels\n")
                .append("- Stronger attackers\n\n")
                .append("Raid ends when you die\n")
                .append("or defeat all pokémon."),

            Component.literal("")
                .append(Component.literal("CUSTOM SETTINGS\n\n").withStyle(ChatFormatting.BOLD))
                .append("Access settings in Mods menu or pressing Y.\n")
                .append("Recommended settings to change:\n\n")
                .append("SelectedLanguage:\n")
                .append("Choose dialogue language.\n\n")
                .append("Characteristics:\n")
                .append("Define personality per Pokemon."),

            Component.literal("")
                .append(Component.literal("PROMPTS AND BEHAVIOR\n\n").withStyle(ChatFormatting.BOLD))
                .append("The Instructs as a whole works as a global prompt.\n")
                .append("You can define how the AI or Pokemon behave, think and responds.\n")
                .append("Each instruct shapes how the response is sent.\n"),

            Component.literal("")
                .append(Component.literal("Developer’s Notes...\n\n").withStyle(ChatFormatting.BOLD))
                .append("You can experience everything the mod has to offer just by talking to the Pokémon.\n\nFeel free to customize it in the config menu or by editing config/cobblebrain\n.json5")
        )

        val content = WrittenBookContent(
            Filterable.passThrough("Cobblebrain Guide"),
            "Vito",
            0,
            pages.map { Filterable.passThrough(it) },
            false
        )

        book.set(DataComponents.WRITTEN_BOOK_CONTENT, content)

        player.addItem(book)
    }
}
