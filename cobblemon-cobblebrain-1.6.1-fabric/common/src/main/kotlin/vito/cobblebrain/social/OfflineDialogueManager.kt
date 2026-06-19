package vito.cobblebrain.social

import net.minecraft.server.level.ServerPlayer
import com.cobblemon.mod.common.pokemon.Pokemon
import vito.cobblebrain.social.DialogueSystem.ScheduledMessage
import vito.cobblebrain.social.DialogueSystem.scheduledMessages
import vito.cobblebrain.sensors.collectWorldContext
import vito.cobblebrain.config.ConfigHandler.config
import vito.cobblebrain.sensors.WorldContext
import vito.cobblebrain.social.OfflineEventHandler.applyContextChance
import vito.cobblebrain.social.OfflineEventHandler.sendNarratorMessage
import java.util.Locale.getDefault

object OfflineDialogueManager {
    fun stretch(fragment: String): String {

        val lastVowelIndex = fragment.indexOfLast {
            it.lowercaseChar() in listOf('a', 'e', 'i', 'o', 'u')
        }

        if (lastVowelIndex == -1) {
            return "$fragment!"
        }

        val vowel = fragment[lastVowelIndex]

        return buildString {
            append(fragment.substring(0, lastVowelIndex + 1))

            repeat((2..4).random()) {
                append(vowel)
            }

            append(fragment.substring(lastVowelIndex + 1))
            append("!")
        }
    }

    fun generateVocalization(name: String): String {

        val cleanName = name
            .replace(Regex("[^a-zA-Z]"), "")
            .ifBlank { return "..." }

        val fullName = cleanName.replaceFirstChar {
            if (it.isLowerCase())
                it.titlecase(getDefault())
            else
                it.toString()
        }

        if (cleanName.length <= 4) {
            return "$fullName!"
        }

        val shortPart =
            cleanName.take(
                minOf(3, cleanName.length)
            ).replaceFirstChar {
                if (it.isLowerCase())
                    it.titlecase(getDefault())
                else
                    it.toString()
            }

        val mediumPart =
            cleanName.take(
                maxOf(5, cleanName.length / 2)
            ).replaceFirstChar {
                if (it.isLowerCase())
                    it.titlecase(getDefault())
                else
                    it.toString()
            }

        val endPart =
            cleanName.takeLast(
                maxOf(4, cleanName.length / 3)
            ).replaceFirstChar {
                if (it.isLowerCase())
                    it.titlecase(getDefault())
                else
                    it.toString()
            }

        return when ((0..11).random()) {

            // Bulba!
            0 -> "$mediumPart!"

            // Bulba-Bulba!
            1 -> "$mediumPart-$mediumPart!"

            // Saur...
            2 -> "$endPart..."

            // Saur... Bulbasaur!
            3 -> "$endPart... $fullName!"

            // Bul-Bulba!
            4 -> "$shortPart-$mediumPart!"

            // Bul... ba... saur?
            5 -> {
                val middle =
                    mediumPart
                        .removePrefix(shortPart)
                        .ifBlank { mediumPart }

                "$shortPart... ${middle.lowercase()}... ${endPart.lowercase()}?"
            }

            // Bulbaaa!
            6 -> stretch(mediumPart)

            // Bulba... Bulba...
            7 -> "$mediumPart... $mediumPart..."

            // Bulba! Bulbasaur!
            8 -> "$mediumPart! $fullName!"

            // Bulbasaur!
            9 -> "$fullName!"

            // Bulba?
            10 -> "$mediumPart?"

            // Saur! Saur!
            else -> "$endPart! $endPart!"
        }
    }

    enum class FeelingContext {
        HOSTILE_MOBS,
        LOW_HP,
        HUNGRY,
        BERRY,
        ITEMS,
        THUNDERSTORM,
        SNOW,
        RAIN,
        NIGHT,
        POKEMON_GROUP,
        DEFAULT
    }

    enum class PersonalityGroup {
        PLAYFUL,
        CALM,
        TIMID,
        AGGRESSIVE,
        EMOTIONAL,
        CONFIDENT,
        SERIOUS
    }

    private fun getPersonalityGroup(
        pokemon: Pokemon
    ): PersonalityGroup {

        return when (
            pokemon.effectiveNature.name.path.lowercase()
        ) {

            "jolly", "hasty", "naive" ->
                PersonalityGroup.PLAYFUL

            "calm", "quiet", "relaxed" ->
                PersonalityGroup.CALM

            "timid", "careful" ->
                PersonalityGroup.TIMID

            "adamant", "brave", "naughty", "rash", "impish" ->
                PersonalityGroup.AGGRESSIVE

            "lonely", "mild", "gentle" ->
                PersonalityGroup.EMOTIONAL

            "bold", "modest", "sassy", "lax" ->
                PersonalityGroup.CONFIDENT

            else ->
                PersonalityGroup.SERIOUS
        }
    }

    fun determineFeelingContext(
        pokemon: Pokemon,
        context: WorldContext
    ): FeelingContext {

        println("POKEMONS POR PERTO: " + context.nearbyPokemon)

        val hpPercent =
            pokemon.currentHealth.toFloat() /
                    pokemon.maxHealth.toFloat() * 100f

        val fullnessPercent =
            pokemon.currentFullness.toFloat() /
                    pokemon.getMaxFullness().toFloat() * 100f

        val weather = context.weather.lowercase()

        return when {

            context.hostileMobs ->
                FeelingContext.HOSTILE_MOBS

            hpPercent < 30f ->
                FeelingContext.LOW_HP

            fullnessPercent < 30f ->
                FeelingContext.HUNGRY

            context.specialBlocks.lowercase().contains("berry") ->
                FeelingContext.BERRY

            context.nearbyItems.isNotBlank() &&
                    context.nearbyItems.lowercase() != "nenhum" ->
                FeelingContext.ITEMS

            weather.contains("storm") ||
                    weather.contains("thunder") ->
                FeelingContext.THUNDERSTORM

            weather.contains("snow") ||
                    weather.contains("blizzard") ->
                FeelingContext.SNOW

            weather.contains("rain") ->
                FeelingContext.RAIN

            context.timeLabel.lowercase().contains("night") ->
                FeelingContext.NIGHT

            context.nearbyPokemon.isNotEmpty() ->
                FeelingContext.POKEMON_GROUP

            else ->
                FeelingContext.DEFAULT
        }
    }

    private fun getContextFeelings(
        feelingContext: FeelingContext
    ): MutableMap<String, Int> {

        return when (feelingContext) {

            FeelingContext.HOSTILE_MOBS -> mutableMapOf(
                "alert" to 20,
                "scared" to 20,
                "nervous" to 15,
                "defensive" to 15,
                "threatened" to 10
            )

            FeelingContext.LOW_HP -> mutableMapOf(
                "hurt" to 20,
                "exhausted" to 15,
                "weak" to 15,
                "recovering" to 10,
                "uncomfortable" to 10
            )

            FeelingContext.HUNGRY -> mutableMapOf(
                "hungry" to 25,
                "searching" to 15,
                "restless" to 10,
                "uncomfortable" to 10
            )

            FeelingContext.BERRY -> mutableMapOf(
                "hungry" to 15,
                "tempted" to 15,
                "interested" to 15,
                "focused" to 10,
                "excited" to 10
            )

            FeelingContext.ITEMS -> mutableMapOf(
                "interested" to 15,
                "focused" to 15,
                "watching" to 10,
                "curious" to 10,
                "tempted" to 10
            )

            FeelingContext.THUNDERSTORM -> mutableMapOf(
                "nervous" to 20,
                "alert" to 15,
                "uneasy" to 15,
                "watching" to 10,
                "threatened" to 10
            )

            FeelingContext.SNOW -> mutableMapOf(
                "cold" to 15,
                "playful" to 15,
                "amazed" to 10,
                "curious" to 10,
                "uncomfortable" to 10
            )

            FeelingContext.RAIN -> mutableMapOf(
                "wet" to 15,
                "relaxed" to 15,
                "gloomy" to 10,
                "resting" to 10,
                "uncomfortable" to 10
            )

            FeelingContext.NIGHT -> mutableMapOf(
                "sleepy" to 15,
                "watching" to 10,
                "quiet" to 10,
                "relaxed" to 10,
                "curious" to 10
            )

            FeelingContext.POKEMON_GROUP -> mutableMapOf(
                "social" to 15,
                "playful" to 15,
                "energetic" to 10,
                "friendly" to 10,
                "happy" to 10
            )

            FeelingContext.DEFAULT -> mutableMapOf()
        }
    }

    fun getNarratorMessage(
        pokemonCount: Int,
        context: FeelingContext,
        pokemonName: String
    ): String {

        val subject =
            if (pokemonCount == 1)
                pokemonName
            else
                "One of your Pokémon"

        return when (context) {

            FeelingContext.HOSTILE_MOBS ->
                "$subject noticed hostile creatures nearby."

            FeelingContext.POKEMON_GROUP ->
                "$subject saw other Pokémon."

            FeelingContext.BERRY ->
                "$subject found food nearby."

            FeelingContext.ITEMS ->
                "$subject found something interesting nearby."

            FeelingContext.LOW_HP ->
                "$subject looks exhausted."

            FeelingContext.HUNGRY ->
                "$subject seems hungry."

            FeelingContext.THUNDERSTORM ->
                "$subject noticed the storm."

            FeelingContext.SNOW ->
                "$subject noticed the snowfall."

            FeelingContext.RAIN ->
                "$subject noticed the rain."

            else ->
                ""
        }
    }
    fun generateFeeling(
        pokemon: Pokemon,
        context: WorldContext,
    ): String? {

        val personality =
            getPersonalityGroup(pokemon)

        val feelingContext =
            applyContextChance(
                determineFeelingContext(
                    pokemon,
                    context
                )
            )

        if (feelingContext == FeelingContext.DEFAULT)
            return null

        val pool =
            getContextFeelings(
                feelingContext
            )

        fun boost(
            feeling: String,
            amount: Int
        ) {
            pool[feeling] =
                (pool[feeling] ?: 0) + amount
        }

        when (personality) {

            PersonalityGroup.PLAYFUL -> {
                boost("playful", 8)
                boost("excited", 6)
                boost("social", 4)
                boost("curious", 4)
            }

            PersonalityGroup.CALM -> {
                boost("relaxed", 8)
                boost("resting", 6)
                boost("friendly", 4)
                boost("sleepy", 4)
            }

            PersonalityGroup.TIMID -> {
                boost("nervous", 8)
                boost("watching", 6)
                boost("suspicious", 6)
                boost("threatened", 4)
            }

            PersonalityGroup.AGGRESSIVE -> {
                boost("defensive", 8)
                boost("challenging", 8)
                boost("alert", 6)
                boost("focused", 4)
            }

            PersonalityGroup.EMOTIONAL -> {
                boost("gloomy", 6)
                boost("friendly", 4)
                boost("curious", 4)
            }

            PersonalityGroup.CONFIDENT -> {
                boost("focused", 8)
                boost("alert", 6)
                boost("energetic", 4)
            }

            PersonalityGroup.SERIOUS -> {
                boost("focused", 8)
                boost("watching", 6)
                boost("alert", 4)
            }
        }

        val totalWeight = pool.values.sum()

        var roll =
            kotlin.random.Random.nextInt(totalWeight)

        for ((feeling, weight) in pool) {

            roll -= weight

            if (roll < 0) {
                return feeling
            }
        }

        return "curious"
    }

    fun generateOfflineResponse(
        pokemon: Pokemon,
        context: WorldContext
    ): String {

        val name =
            pokemon.nickname?.string
                ?: pokemon.species.name

        val vocal =
            generateVocalization(name)

        val feeling =
            generateFeeling(
                pokemon,
                context
            )

        return if (feeling == null) {
            vocal
        } else {
            "$vocal ($feeling)"
        }
    }

    fun handleOfflineTalk(player: ServerPlayer) {
        // Quest rolling for offline mode
        val context = collectWorldContext(player)
        val wildEntity = context.nearbyPokemonEntities.randomOrNull()
        if (wildEntity != null) {
            val giver = wildEntity.pokemon
            val activeQuestsList = CobblebrainWorldSave.getActiveQuests(player)
            val hasActiveQuest = activeQuestsList.any { it.get("status").asString == "IN_PROGRESS" }
            if (!hasActiveQuest && Math.random() <= config.wildQuestChance) {
                val roll = (1..3).random() // 1 = Item, 2 = Battle, 3 = Treasure (Advice 0 is blocked)
                val giverName = giver.nickname?.string ?: giver.species.name
                when (roll) {
                    1 -> {
                        CobblebrainWorldSave.createItemQuest(player, wildEntity)
                        player.sendSystemMessage(
                            net.minecraft.network.chat.Component.literal(
                                "$giverName has started an ITEM quest!"
                            ).withStyle(net.minecraft.ChatFormatting.YELLOW)
                        )
                    }
                    2 -> {
                        CobblebrainWorldSave.createBattleQuest(player, wildEntity)
                        player.sendSystemMessage(
                            net.minecraft.network.chat.Component.literal(
                                "$giverName has started a BATTLE quest!"
                            ).withStyle(net.minecraft.ChatFormatting.YELLOW)
                        )
                    }
                    3 -> {
                        CobblebrainWorldSave.createTreasureQuest(player, wildEntity)
                        player.sendSystemMessage(
                            net.minecraft.network.chat.Component.literal(
                                "$giverName needs help finding LOST ITEMS!"
                            ).withStyle(net.minecraft.ChatFormatting.YELLOW)
                        )
                    }
                }
            }
        }

        val activePokemon = PokemonQuery.findActivePokemon(player)
        if (activePokemon.isEmpty()) return

        val server = player.server
        val startTick = server.tickCount.toLong()
        val newMessages = mutableListOf<ScheduledMessage>()

        val currentContext =
            determineFeelingContext(
                activePokemon.first(),
                context
            )

        sendNarratorMessage(
            player,
            activePokemon,
            currentContext,
        )
        AmbientReactionManager.triggerReaction(player, activePokemon, currentContext)

        activePokemon.forEachIndexed { index, pokemon ->
            val name = pokemon.nickname?.string ?: pokemon.species.name
            val responseText = generateOfflineResponse(pokemon, context)
            val line = "$name: $responseText"
            
            newMessages.add(
                ScheduledMessage(
                    player = player,
                    text = line,
                    sendAtTick = if (index == 0) startTick else startTick + (index * 100),
                    speaker = pokemon,
                    pitchMod = 0f
                )
            )
        }

        scheduledMessages[player.uuid] = newMessages.toMutableList()
    }
}
