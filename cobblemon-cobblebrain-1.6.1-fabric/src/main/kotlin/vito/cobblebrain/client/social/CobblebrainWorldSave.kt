package vito.cobblebrain.client.social

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
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
    }

    fun save() {
        FileWriter(saveFile).use { writer ->
            GsonBuilder().setPrettyPrinting().create().toJson(data, writer)
        }
    }

    fun load(player: ServerPlayer): List<Quest> {
        return quests.filter { it.player == player }
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
