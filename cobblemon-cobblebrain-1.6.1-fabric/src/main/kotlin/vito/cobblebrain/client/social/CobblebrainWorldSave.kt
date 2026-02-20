package vito.cobblebrain.client.social

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.level.ServerPlayer

object CobblebrainWorldSave {
    data class Quest(
        val player: ServerPlayer,
        val type: String,
        var active: Boolean,
        val itemName: String? = null,
        val amount: Int? = null
    )

    private val saveFile = File("cobblebrain-ai/cobblebrainWorldSave.json")
    private val quests: MutableList<Quest> = mutableListOf()
    var data: JsonObject = JsonObject()

    init {
        if (saveFile.exists()) {
            data = JsonParser.parseReader(FileReader(saveFile)).asJsonObject
        } else {
            data = JsonObject().apply {
                add("karma", JsonObject())
                add("quests", JsonObject().apply {
                    add("active", JsonArray())
                    add("completed", JsonArray())
                    add("abandoned", JsonArray())
                })
            }
            save()
        }

        // REGISTRA O TICK AQUI
        ServerTickEvents.END_SERVER_TICK.register {
            handleFollowers()
        }
    }

    fun save() {
        FileWriter(saveFile).use { writer ->
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
            addProperty("giverUuid", giver.uuid.toString())
            addProperty("type", "BATTLE")
            addProperty("targetSpecies", targetSpecies)
            addProperty("status", "IN_PROGRESS")
            addProperty("giverSpecies", giver.pokemon.species.name)
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
            addProperty("giverUuid", giver.uuid.toString())
            addProperty("type", "ITEM")
            addProperty("target", target)
            addProperty("amount", amount)
            addProperty("status", "IN_PROGRESS")
            addProperty("giverSpecies", giver.pokemon.species.name)
            giver.pokemon.nickname?.string?.let { addProperty("giverNickname", it)}
        }

        data.getAsJsonObject("quests").getAsJsonArray("active").add(questObj)
        save()

        val quest = Quest(player, "ITEM", true, target, amount)
        quests.add(quest)

        val giverName = giver.pokemon.nickname?.string ?: giver.pokemon.species.resourceIdentifier.path
        println("IMPORTANT: $giverName asks you to bring $amount $target(s)!")
        giver.navigation.moveTo(player.x, player.y, player.z, 1.0)

        return quest
    }

    fun createAdviceQuest(player: ServerPlayer, giver: PokemonEntity) {
        val questObj = JsonObject().apply {
            addProperty("giverUuid", giver.uuid.toString())
            addProperty("type", "ADVICE")
            addProperty("status", "IN_PROGRESS")
            addProperty("giverSpecies", giver.pokemon.species.name)
            giver.pokemon.nickname?.string?.let { addProperty("giverNickname", it) }
        }

        data.getAsJsonObject("quests").getAsJsonArray("active").add(questObj)
        save()

        val quest = Quest(player, "ADVICE", true)
        quests.add(quest)

        val giverName = giver.pokemon.nickname?.string ?: giver.pokemon.species.resourceIdentifier.path
        println("IMPORTANT: $giverName has started an ADVICE quest! It wants to talk to the player.")
        giver.navigation.moveTo(player.x, player.y, player.z, 1.0)
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

    fun getActiveQuest(): JsonObject? {
        val activeArray = data.getAsJsonObject("quests").getAsJsonArray("active")
        return activeArray.lastOrNull {
            val obj = it.asJsonObject
            obj.get("status").asString == "IN_PROGRESS"
        }?.asJsonObject
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
    fun moveQuest(giverUuid: String, type: String, newStatus: String) {
        println("[DEBUG] moveQuest chamado com giverUuid=$giverUuid, type=$type, newStatus=$newStatus")

        val questsObj = data.getAsJsonObject("quests")
        val activeArray = questsObj.getAsJsonArray("active")
        val completedArray = questsObj.getAsJsonArray("completed")
        val abandonedArray = questsObj.getAsJsonArray("abandoned")

        val questElement = activeArray.firstOrNull {
            val obj = it.asJsonObject
            obj.get("giverUuid").asString == giverUuid &&
                    obj.get("type").asString == type
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

    private fun handleFollowers() {
        val iterator = followers.iterator()

        while (iterator.hasNext()) {
            val entry = iterator.next()
            val (pokemon, player) = entry.value

            if (!pokemon.isAlive || pokemon.isRemoved) {
                iterator.remove()
                continue
            }

            val distance = pokemon.distanceTo(player)

            val followDist = 5.5
            val teleportDist = 25.0

            when {
                distance > teleportDist -> {
                    pokemon.teleportTo(
                        player.x + pokemon.level().random.nextInt(-2, 3),
                        player.y,
                        player.z + pokemon.level().random.nextInt(-2, 3)
                    )
                }

                distance > followDist -> {
                    pokemon.navigation.moveTo(player, 0.65)
                }
            }

            pokemon.lookAt(player, 30f, 30f)
        }
    }

    fun adjustKarma(giverName: String, delta: Int) {
        println("[DEBUG] adjustKarma chamado para $giverName com delta=$delta")

        val karmaObj = data.getAsJsonObject("karma")
        val current = karmaObj.get(giverName)?.asInt ?: 0
        println("[DEBUG] Karma atual de $giverName = $current")

        karmaObj.addProperty(giverName, current + delta)
        println("[DEBUG] Karma atualizado para ${current + delta}")

        save()
        println("[DEBUG] Arquivo salvo após adjustKarma")
        debugQuests()
    }

    // Debug: imprime estado atual das quests
    fun debugQuests() {
        val questsObj = data.getAsJsonObject("quests")
        println("Active quests: ${questsObj.getAsJsonArray("active")}")
        println("Completed quests: ${questsObj.getAsJsonArray("completed")}")
        println("Abandoned quests: ${questsObj.getAsJsonArray("abandoned")}")
        println("Karma: ${data.getAsJsonObject("karma")}")
    }
}
