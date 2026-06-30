package vito.cobblebrain.social

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
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
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BarrelBlockEntity
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import kotlin.random.Random

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
        val target: String? = null,
        val amount: Int? = null,
        val questSummary: String? = null,
        val storyId: String? = null
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
            data = JsonObject()
        }
        checkDataStructure()
        save()
    }

    private fun checkDataStructure() {
        if (!data.has("received_guide")) data.add("received_guide", JsonArray())
        if (!data.has("karma")) data.add("karma", JsonObject())
        if (!data.has("kill_count")) data.add("kill_count", JsonObject())
        if (!data.has("last_session_summary")) data.add("last_session_summary", JsonObject())
        if (!data.has("player_cooldowns")) data.add("player_cooldowns", JsonObject())
        if (!data.has("quests")) {
            data.add("quests", JsonObject().apply {
                add("active_story", JsonObject())
                add("active_secondary", JsonArray())
                add("completed", JsonArray())
                add("abandoned", JsonArray())
            })
        }
    }

    fun setSessionSummary(playerUuid: String, summary: String) {
        val summaries = data.getAsJsonObject("last_session_summary") ?: JsonObject().also { data.add("last_session_summary", it) }
        summaries.addProperty(playerUuid, summary)
        save()
    }

    fun getSessionSummary(playerUuid: String): String? {
        val summaries = data.getAsJsonObject("last_session_summary") ?: return null
        return summaries.get(playerUuid)?.asString
    }

    fun clearSessionSummary(playerUuid: String) {
        val summaries = data.getAsJsonObject("last_session_summary") ?: return
        if (summaries.has(playerUuid)) {
            summaries.remove(playerUuid)
            save()
        }
    }

    fun setPlayerCooldown(playerUuid: String, key: String, timestamp: Long) {
        val cooldownsRoot = data.getAsJsonObject("player_cooldowns") ?: JsonObject().also { data.add("player_cooldowns", it) }
        val playerObj = cooldownsRoot.getAsJsonObject(playerUuid) ?: JsonObject().also { cooldownsRoot.add(playerUuid, it) }
        playerObj.addProperty(key, timestamp)
        save()
    }

    fun getPlayerCooldown(playerUuid: String, key: String): Long {
        val cooldownsRoot = data.getAsJsonObject("player_cooldowns") ?: return 0L
        val playerObj = cooldownsRoot.getAsJsonObject(playerUuid) ?: return 0L
        return playerObj.get(key)?.asLong ?: 0L
    }

    private fun ensureGuideArray() {
        if (!data.has("received_guide")) {
            data.add("received_guide", JsonArray())
        }
    }

    fun hasReceivedGuide(player: ServerPlayer): Boolean {
        ensureGuideArray()
        return data.getAsJsonArray("received_guide").any { it.asString == player.uuid.toString() }
    }

    fun markGuideReceived(player: ServerPlayer) {
        ensureGuideArray()
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

    private fun ensureQuestsInitialized() {
        val quests = data.getAsJsonObject("quests") ?: JsonObject().also { data.add("quests", it) }
        
        if (!quests.has("active_story") || !quests.get("active_story").isJsonObject) {
            quests.add("active_story", JsonObject())
        }
        if (!quests.has("active_secondary") || !quests.get("active_secondary").isJsonArray) {
            quests.add("active_secondary", JsonArray())
        }
        if (!quests.has("completed") || !quests.get("completed").isJsonArray) {
            quests.add("completed", JsonArray())
        }
        if (!quests.has("abandoned") || !quests.get("abandoned").isJsonArray) {
            quests.add("abandoned", JsonArray())
        }
    }

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

    fun getSecondaryQuestsForAll(): JsonArray {
        ensureQuestsInitialized()
        val all = JsonArray()
        val questsObj = data.getAsJsonObject("quests")
        
        // Add secondary
        val secondary = questsObj.getAsJsonArray("active_secondary")
        secondary.forEach { all.add(it) }
        
        // Add story if exists
        val story = questsObj.get("active_story")
        if (story != null && story.isJsonObject && story.asJsonObject.size() > 0) {
            all.add(story)
        }
        
        return all
    }

    fun failQuest(playerUuid: String, giverUuid: String, type: String) {
        moveQuest(playerUuid, giverUuid, type, "FAILED")
    }

    fun createBattleQuest(player: ServerPlayer, giver: PokemonEntity, storyId: String? = null): Quest {
        ensureQuestsInitialized()
        val targetSpecies = battleSpecies.random()

        val level = player.serverLevel()

        // Calcula o nível alvo baseado no Pokémon mais forte do jogador
        val strongestLevel = PokemonQuery.findActivePokemon(player).maxOfOrNull { it.level } ?: 20
        val minLevel = maxOf(5, strongestLevel - 2)
        val maxLevel = strongestLevel + 8
        val targetLevel = Random.nextInt(minLevel, maxLevel + 1)

        val nearbyWildPokemon = level.getEntitiesOfClass(PokemonEntity::class.java, player.boundingBox.inflate(64.0)) {
            it.pokemon.getOwnerUUID() == null && it.pokemon.species.name.equals(targetSpecies, ignoreCase = true)
        }.minByOrNull { it.distanceTo(player) }

        val targetPokemon = nearbyWildPokemon ?: run {
            val spawnX = player.blockX + (if (Random.nextBoolean()) (20..40).random() else (-40..-20).random())
            val spawnZ = player.blockZ + (if (Random.nextBoolean()) (20..40).random() else (-40..-20).random())
            val spawnY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, spawnX, spawnZ)
            WorldEventsSystem.spawnPokemon(level, targetSpecies, spawnX, spawnY, spawnZ, targetLevel)
        }

        targetPokemon?.addEffect(
            MobEffectInstance(MobEffects.GLOWING, 999999, 0, false, false)
        )

        val questObj = JsonObject().apply {
            addProperty("ownerUuid", player.uuid.toString())
            addProperty("giverUuid", giver.uuid.toString())
            addProperty("type", "BATTLE")
            addProperty("targetSpecies", targetSpecies)
            addProperty("status", "IN_PROGRESS")
            addProperty("giverSpecies", giver.pokemon.species.name)
            addProperty("questSummary", "This is a Battle quest!")
            addProperty("storyId", storyId ?: "generic")
            giver.pokemon.nickname?.string?.let { addProperty("giverNickname", it) }

            // novos campos
            addProperty("startTime", System.currentTimeMillis())
            
            val giverName = giver.pokemon.nickname?.string ?: giver.pokemon.species.resourceIdentifier.path
            addProperty("giverName", giverName)
        }

        val listName = if (storyId != null && storyId != "generic") "active_story" else "active_secondary"
        if (listName == "active_story") {
            data.getAsJsonObject("quests").add(listName, questObj)
        } else {
            data.getAsJsonObject("quests").getAsJsonArray(listName).add(questObj)
        }
        save()

        val quest = Quest(player, "BATTLE", true, storyId = storyId)
        quests.add(quest)

        val giverName = giver.pokemon.nickname?.string ?: giver.pokemon.species.resourceIdentifier.path
        println("IMPORTANT: $giverName asks you to defeat a $targetSpecies in battle!")

        startFollowingPlayer(giver, player)

        return quest
    }

    fun createItemQuest(player: ServerPlayer, giver: PokemonEntity, storyId: String? = null): Quest {
        ensureQuestsInitialized()
        val items = listOf(
            "leather", "bone", "stick", "bread", "sugar", "apple", "sweet_berries",
            "red_apricorn", "blue_apricorn", "yellow_apricorn", "green_apricorn", 
            "white_apricorn", "black_apricorn", "pink_apricorn",
            "oran_berry", "pecha_berry", "leppa_berry", "cheri_berry", 
            "rawst_berry", "aspear_berry", "persim_berry", "lum_berry", "sitrus_berry",
            "coal", "copper_ingot", "iron_ingot", "gold_ingot"
        )
        val target = items.random()
        val baseAmount = (3..10).random()

        val amount = when {
            target == "stick" -> baseAmount * 10
            target in listOf("leather", "bone", "coal", "bread", "sugar", "apple", "sweet_berries") || target.endsWith("_berry") -> baseAmount * 2
            else -> baseAmount
        }

        val questObj = JsonObject().apply {
            addProperty("ownerUuid", player.uuid.toString())
            addProperty("giverUuid", giver.uuid.toString())
            addProperty("type", "ITEM")
            addProperty("target", target)
            addProperty("amount", amount)
            addProperty("status", "IN_PROGRESS")
            addProperty("giverSpecies", giver.pokemon.species.name)
            addProperty("questSummary", "This is a Item quest!")
            addProperty("storyId", storyId ?: "generic")
            giver.pokemon.nickname?.string?.let { addProperty("giverNickname", it) }
            
            val giverName = giver.pokemon.nickname?.string ?: giver.pokemon.species.resourceIdentifier.path
            addProperty("giverName", giverName)
        }

        val listName = if (storyId != null && storyId != "generic") "active_story" else "active_secondary"
        if (listName == "active_story") {
            data.getAsJsonObject("quests").add(listName, questObj)
        } else {
            data.getAsJsonObject("quests").getAsJsonArray(listName).add(questObj)
        }
        save()

        val quest = Quest(player, "ITEM", true, target = target, amount = amount, storyId = storyId)
        quests.add(quest)

        val giverName = giver.pokemon.nickname?.string ?: giver.pokemon.species.resourceIdentifier.path
        println("IMPORTANT: $giverName asks you to bring $amount $target(s)!")
        startFollowingPlayer(giver, player)
        return quest
    }

    fun createAdviceQuest(player: ServerPlayer, giver: PokemonEntity, storyId: String? = null) {
        ensureQuestsInitialized()
        val issues = listOf(
            // --- pratical problems (survival) ---
            "hunger", "need for strength", "rivalry", "laziness", "procrastination", 
            "obsession with power", "struggle with discipline", "territory dispute", 
            "finding shelter", "noisy environment", "uncomfortable nest", 
            "lack of exercise", "extreme heat", "extreme cold", "dehydration", 
            "fear of storms", "noisy neighbors", "clumsiness", "lack of sleep", "fear of the dark",
            "choice of food", "combat techniques", "choice of where to live", "conflict with other pokemons",
            "fear of battle", "fear of humans",

            // --- sentimental problems ---
            "loneliness", "lost friend", "confusion", "boredom", 
            "fear of evolving", "lack of confidence", "homesickness",
            "curiosity", "wanderlust", "insecurity", 
            "desire for adventure", "fear of failure", "regret", "curiosity about humans", 
            "overthinking", "perfectionism", "guilt",
            "impatience", "stubbornness", "nightmares", "fear of the unknown", 
            "distrust of trainers", "desire to be unique", "curiosity about evolution", 
            "desire for freedom", "purpose of life"
        )
        val selectedIssue = issues.random()

        val questObj = JsonObject().apply {
            addProperty("ownerUuid", player.uuid.toString())
            addProperty("giverUuid", giver.uuid.toString())
            addProperty("type", "ADVICE")
            addProperty("status", "IN_PROGRESS")
            addProperty("giverSpecies", giver.pokemon.species.name)
            addProperty("questSummary", "Wants advice about $selectedIssue")
            addProperty("storyId", storyId ?: "generic")
            addProperty("issue", selectedIssue)
            addProperty("points", 0) // Start with 0 points
            giver.pokemon.nickname?.string?.let { addProperty("giverNickname", it) }
            
            val giverName = giver.pokemon.nickname?.string ?: giver.pokemon.species.resourceIdentifier.path
            addProperty("giverName", giverName)
        }

        val listName = if (storyId != null && storyId != "generic") "active_story" else "active_secondary"
        if (listName == "active_story") {
            data.getAsJsonObject("quests").add(listName, questObj)
        } else {
            data.getAsJsonObject("quests").getAsJsonArray(listName).add(questObj)
        }
        save()

        val quest = Quest(player, "ADVICE", true, storyId = storyId)
        quests.add(quest)

        val giverName = giver.pokemon.nickname?.string ?: giver.pokemon.species.resourceIdentifier.path
        println("IMPORTANT: $giverName has started an ADVICE quest about $selectedIssue!")
        startFollowingPlayer(giver, player)
    }

    fun createLocationQuest(player: ServerPlayer, giver: PokemonEntity, storyId: String? = null): Quest {
        ensureQuestsInitialized()
        val level = player.level()
        val rand = java.util.Random()

        val targetX = player.blockX + rand.nextInt(800) - 400
        val targetZ = player.blockZ + rand.nextInt(800) - 400
        val targetY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, targetX, targetZ)

        val questObj = JsonObject().apply {
            addProperty("ownerUuid", player.uuid.toString())
            addProperty("giverUuid", giver.uuid.toString())
            addProperty("type", "LOCATION")
            addProperty("targetX", targetX)
            addProperty("targetY", targetY)
            addProperty("targetZ", targetZ)
            addProperty("status", "IN_PROGRESS")
            addProperty("giverSpecies", giver.pokemon.species.name)
            addProperty("questSummary", "Go to the marked location")
            addProperty("storyId", storyId ?: "generic")
        }

        val listName = if (storyId != null && storyId != "generic") "active_story" else "active_secondary"
        if (listName == "active_story") {
            data.getAsJsonObject("quests").add(listName, questObj)
        } else {
            data.getAsJsonObject("quests").getAsJsonArray(listName).add(questObj)
        }
        save()

        val quest = Quest(player, "LOCATION", true, storyId = storyId)
        quests.add(quest)

        println("IMPORTANT: Go to coordinates: $targetX $targetY $targetZ")

        startFollowingPlayer(giver, player)

        return quest
    }

    fun createTreasureQuest(player: ServerPlayer, giver: PokemonEntity, storyId: String? = null): Quest {
        ensureQuestsInitialized()
        val level = player.serverLevel()
        val rand = java.util.Random()

        // 1. Sorteia local
        val targetX = player.blockX + rand.nextInt(800) - 400
        val targetZ = player.blockZ + rand.nextInt(800) - 400
        val surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, targetX, targetZ)
        
        val targetY = surfaceY
        val barrelPos = BlockPos(targetX, targetY, targetZ)

        // 2. Coloca o barril e itens
        level.setBlock(barrelPos, Blocks.BARREL.defaultBlockState(), 3)
        val barrelEntity = level.getBlockEntity(barrelPos) as? BarrelBlockEntity
        if (barrelEntity != null) {
            val lootItems = listOf(
                Items.STICK, Items.APPLE, Items.WHEAT_SEEDS, Items.OAK_SAPLING, 
                Items.SWEET_BERRIES, Items.COAL, Items.FLINT, Items.BONE
            )
            // Adiciona 3 a 6 itens aleatórios
            val itemCount = rand.nextInt(4) + 3
            for (i in 0 until itemCount) {
                val slot = rand.nextInt(barrelEntity.containerSize)
                val stack = ItemStack(lootItems.random(), rand.nextInt(3) + 1)
                barrelEntity.setItem(slot, stack)
            }
        }

        val questObj = JsonObject().apply {
            addProperty("ownerUuid", player.uuid.toString())
            addProperty("giverUuid", giver.uuid.toString())
            addProperty("type", "TREASURE")
            addProperty("targetX", targetX)
            addProperty("targetY", targetY)
            addProperty("targetZ", targetZ)
            addProperty("status", "IN_PROGRESS")
            addProperty("giverSpecies", giver.pokemon.species.name)
            addProperty("questSummary", "Find the hidden barrel near ($targetX, $targetZ)")
            addProperty("storyId", storyId ?: "generic")
            
            val giverName = giver.pokemon.nickname?.string ?: giver.pokemon.species.resourceIdentifier.path
            addProperty("giverName", giverName)
        }

        val listName = if (storyId != null && storyId != "generic") "active_story" else "active_secondary"
        if (listName == "active_story") {
            data.getAsJsonObject("quests").add(listName, questObj)
        } else {
            data.getAsJsonObject("quests").getAsJsonArray(listName).add(questObj)
        }
        save()

        val quest = Quest(player, "TREASURE", true, storyId = storyId)
        quests.add(quest)

        println("IMPORTANT: A treasure is hidden at $targetX $targetY $targetZ")
        startFollowingPlayer(giver, player)

        return quest
    }

    fun findQuest(giverUuid: String, type: String, status: String? = null): JsonObject? {
        val activeArray = data.getAsJsonObject("quests").getAsJsonArray("active_secondary")
        val storyObj = data.getAsJsonObject("quests").getAsJsonObject("active_story")
        
        val list = mutableListOf<JsonObject>()
        if (storyObj.size() > 0) list.add(storyObj)
        activeArray.forEach { list.add(it.asJsonObject) }
        
        return list.firstOrNull {
            val obj = it.asJsonObject
            obj.get("giverUuid").asString == giverUuid &&
                    obj.get("type").asString == type &&
                    (status == null || obj.get("status").asString == status)
        }
    }

    fun getActiveItemQuest(player: ServerPlayer): JsonObject? {
        return getActiveQuests(player).firstOrNull { it.get("type").asString == "ITEM" }
    }

    fun getStoryQuest(player: ServerPlayer): JsonObject? {
        val quests = data.getAsJsonObject("quests") ?: return null
        val story = quests.get("active_story")
        if (story != null && story.isJsonObject && !story.asJsonObject.asMap().isEmpty()) {
            val sObj = story.asJsonObject
            if (sObj.get("ownerUuid")?.asString == player.uuid.toString()) {
                return sObj
            }
        }
        return null
    }

    fun getSecondaryQuests(player: ServerPlayer): List<JsonObject> {
        val quests = data.getAsJsonObject("quests") ?: return emptyList()
        val secondary = quests.getAsJsonArray("active_secondary") ?: return emptyList()
        return secondary.map { it.asJsonObject }.filter { it.get("ownerUuid")?.asString == player.uuid.toString() }
    }

    fun getActiveQuests(player: ServerPlayer): List<JsonObject> {
        val result = mutableListOf<JsonObject>()
        getStoryQuest(player)?.let { result.add(it) }
        result.addAll(getSecondaryQuests(player))
        return result
    }

    fun getActiveQuest(player: ServerPlayer): JsonObject? {
        return getStoryQuest(player) ?: getSecondaryQuests(player).lastOrNull()
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

        val questsObj = data.getAsJsonObject("quests") ?: return
        val storyObj = questsObj.getAsJsonObject("active_story") ?: JsonObject()
        val secondaryArray = questsObj.getAsJsonArray("active_secondary") ?: JsonArray()
        val completedArray = questsObj.getAsJsonArray("completed") ?: JsonArray()
        val abandonedArray = questsObj.getAsJsonArray("abandoned") ?: JsonArray()

        var questObj: JsonObject?
        var foundInStory = false
        var foundElement: JsonElement? = null

        // Check story
        if (!storyObj.asMap().isEmpty() && 
            storyObj.get("giverUuid")?.asString == giverUuid &&
            storyObj.get("type").asString == type &&
            storyObj.get("ownerUuid").asString == ownerUuid) {
            questObj = storyObj
            foundInStory = true
        } else {
            // Check secondary
            foundElement = secondaryArray.firstOrNull {
                val obj = it.asJsonObject
                obj.get("giverUuid").asString == giverUuid &&
                        obj.get("type").asString == type &&
                        obj.get("ownerUuid").asString == ownerUuid
            }
            questObj = foundElement?.asJsonObject
        }

        if (questObj == null) {
            println("[DEBUG] No active quest found to move")
            return
        }

        println("[DEBUG] Quest found: $questObj")

        questObj.addProperty("status", newStatus)

        // remove da lista de ativos
        if (foundInStory) {
            questsObj.add("active_story", JsonObject())
        } else {
            secondaryArray.remove(foundElement)
        }
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

        // Mensagens de desbloqueio de Tier
        val thresholds = mapOf(
            3 to "UNCOMMON",
            7 to "RARE",
            12 to "EPIC"
        )

        for ((limit, tier) in thresholds) {
            if (current < limit && newValue >= limit) {
                player.sendSystemMessage(
                    Component.literal("The Pokémon ")
                        .append(Component.literal(species).withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal(" appreciate your help! Now you can receive ").withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(tier).withStyle(if (tier == "EPIC") ChatFormatting.LIGHT_PURPLE else if (tier == "RARE") ChatFormatting.GOLD else ChatFormatting.AQUA))
                        .append(Component.literal(" items from this species!").withStyle(ChatFormatting.WHITE))
                )
            }
        }

        println("[DEBUG] Karma atualizado: $species = $newValue para ${player.name.string}")
        save()
    }

    // Debug: imprime estado atual das quests
    fun debugQuests() {
        val questsObj = data.getAsJsonObject("quests")
        println("Active story quest: ${questsObj.getAsJsonObject("active_story")}")
        println("Active secondary quests: ${questsObj.getAsJsonArray("active_secondary")}")
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
                .append(clickablePage("Karma", 16)).append("\n")
                .append(clickablePage("Raids", 18)).append("\n")
                .append(clickablePage("Other Mechanics", 19)).append("\n")
                .append(clickablePage("Custom Settings", 20)).append("\n")
                .append(clickablePage("Developer’s Notes", 22)).append("\n"),

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
                .append("There are currently 4 types of quests."),

            Component.literal("")
                .append(Component.literal("QUEST TYPES\n\n").withStyle(ChatFormatting.BOLD))
                .append("ITEM QUEST\n")
                .append("Drop specific items to the Pokemon.\n\n")
                .append("BATTLE QUEST\n")
                .append("Defeat the target in a Pokemon battle.\n")
                .append("Killing outside battle does not count.\n\n"),

            Component.literal("")
                .append("ADVICE QUEST\n")
                .append("Give some advice to make the Pokemon happy.\n")
                .append("Multiple solutions are possible."),

            Component.literal("")
                .append("LOST ITEM SEARCH\n")
                .append("find the lost Pokemon item barrel\n" +
                        "you must find and open it to complete the mission\n"),

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
                .append("Better karma = Better rewards in quests.\n")
                .append("karma under -6 may trigger raids."),

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
                .append(Component.literal("OTHER MECHANICS\n\n").withStyle(ChatFormatting.BOLD))
                .append("Guaranteed catch: convince the Pokémon to be caught and get a 100% chance of capture on the next throw.\n\n")
                .append("Food effect: When using the EAT command, certain foods give effects and XP to Pokémon."),

            Component.literal("")
                .append(Component.literal("CUSTOM SETTINGS\n\n").withStyle(ChatFormatting.BOLD))
                .append("Access the mod settings by pressing Y.\n")
                .append("Hover your mouse over the options to see what they do!\n\n")
                .append("Recommended settings: Selected Language, Instruct, and Characteristics."),

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
