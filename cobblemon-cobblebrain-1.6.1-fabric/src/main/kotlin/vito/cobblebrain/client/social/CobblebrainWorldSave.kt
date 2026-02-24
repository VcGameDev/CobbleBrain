package vito.cobblebrain.client.social

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.storage.LevelResource
import net.minecraft.world.entity.ai.goal.Goal
import vito.cobblebrain.mixin.MobAccessor

class FollowPlayerGoal(
    val pokemon: PokemonEntity,
    val player: ServerPlayer,
    val speed: Double,
    val minDistance: Float,
    val maxDistance: Double
) : Goal() {

    override fun canUse(): Boolean {
        return player.isAlive && pokemon.distanceTo(player) > minDistance
    }

    override fun canContinueToUse(): Boolean {
        return pokemon.distanceTo(player) > minDistance && pokemon.isAlive
    }

    override fun start() {
        // nada aqui, só start do Goal
    }

    override fun tick() {
        if (pokemon.distanceTo(player) > maxDistance) {
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

    fun save() {
        val file = saveFile
        FileWriter(file).use { writer ->
            GsonBuilder().setPrettyPrinting().create().toJson(data, writer)
        }
    }

    fun load(player: ServerPlayer): List<Quest> {
        return quests.filter { it.player == player }
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

    private val followers = mutableMapOf<String, Pair<PokemonEntity, ServerPlayer>>()

    private fun startFollowingPlayer(giver: PokemonEntity, player: ServerPlayer) {
        giver.setPersistenceRequired()
        giver.isNoAi = false

        followers[giver.uuid.toString()] = Pair(giver, player)
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
        val items = listOf("sweet_berries", "apple", "coal", "cooper_ingot")
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
            val accessor = giver as MobAccessor
            accessor.getGoalSelector().addGoal(1, FollowPlayerGoal(giver, player, 0.5, 2.5f, 30.0))        }

        data.getAsJsonObject("quests").getAsJsonArray("active").add(questObj)
        save()

        val quest = Quest(player, "ITEM", true, target, amount)
        quests.add(quest)

        val giverName = giver.pokemon.nickname?.string ?: giver.pokemon.species.resourceIdentifier.path
        println("IMPORTANT: $giverName asks you to bring $amount $target(s)!")
        val accessor = giver as MobAccessor
        accessor.getGoalSelector().addGoal(1, FollowPlayerGoal(giver, player, 0.5, 2.5f, 30.0))
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
        val accessor = giver as MobAccessor
        accessor.getGoalSelector().addGoal(1, FollowPlayerGoal(giver, player, 0.5, 2.5f, 30.0))    }

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
        println("[DEBUG] moveQuest chamado com giverUuid=$giverUuid, type=$type, newStatus=$newStatus")

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
            println("[DEBUG] Nenhuma quest encontrada em active para mover")
            return
        }

        val questObj = questElement.asJsonObject
        println("[DEBUG] Quest encontrada: $questObj")

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
        println("[DEBUG] Arquivo salvo após moveQuest")
        debugQuests()

        // Se o Pokémon estava seguindo, para de seguir
        followers[giverUuid]?.let { pair ->
            val pokemon = pair.first
            pokemon.navigation.stop()
            followers.remove(giverUuid)
            println("[DEBUG] Follower ${pokemon.pokemon.species.name} parou de seguir o jogador (quest $newStatus)")
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
}
