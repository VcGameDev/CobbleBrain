package vito.cobblebrain.social

import com.cobblemon.mod.common.api.events.battles.BattleFledEvent
import com.cobblemon.mod.common.api.events.battles.BattleStartedEvent
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent
import com.cobblemon.mod.common.api.events.pokemon.PokemonSentEvent
import com.cobblemon.mod.common.battles.BattleRegistry
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.CobblemonItems
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import kotlin.random.Random
import com.cobblemon.mod.common.pokemon.Pokemon
import com.google.gson.JsonObject
import net.minecraft.ChatFormatting
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvent
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundSource
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import vito.cobblebrain.client.social.CobblebrainWorldSave
import vito.cobblebrain.client.social.CobblebrainWorldSave.adjustKarma
import vito.cobblebrain.client.social.CobblebrainWorldSave.adjustKillCount
import vito.cobblebrain.config.ConfigHandler.config
import vito.cobblebrain.config.ClientConfigHandler.clientConfig
import vito.cobblebrain.currentServer
import vito.cobblebrain.sensors.CommandState
import vito.cobblebrain.sensors.MemoryStore.loadPokemonMemories
import vito.cobblebrain.sensors.MemoryStore.savePokemonMemory
import vito.cobblebrain.sensors.collectWorldContext
import vito.cobblebrain.sensors.parseCommand
import java.lang.Math.toDegrees
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.atan2
import kotlin.math.sqrt

object DialogueSystem {
    val justSentMessage: MutableMap<UUID, Boolean> = ConcurrentHashMap()
    val scheduledMessages: MutableMap<UUID, MutableList<ScheduledMessage>> = ConcurrentHashMap()

    data class ScheduledMessage(
        val player: ServerPlayer,
        val text: String,
        val sendAtTick: Long,
        val speaker: Pokemon? = null
    )

    // Estado social para manter o olhar
    private var currentSpeaker: Pokemon? = null
    private var speakerUntilTick: Long = 0L
    private val currentViewers = mutableListOf<Pokemon>()

    // guarda o último momento em que cada jogador disparou a lógica
    private val lastPrompt: MutableMap<UUID, Long> = ConcurrentHashMap()

    // guarda o pitch atual de cada Pokémon ativo
    private val pokemonPitchMap = mutableMapOf<UUID, Float>()

    private val bubbleProgress = mutableMapOf<UUID, Int>()          // standUuid -> chars revelados
    private val bubbleText = mutableMapOf<UUID, String>()           // standUuid -> texto completo
    private val bubbleSpeed = mutableMapOf<UUID, Int>()             // standUuid -> chars por tick (opcional)


    fun onPlayerJoin(player: ServerPlayer) {
        player.sendSystemMessage(
            Component.literal("Welcome to Cobblebrain! Use the command ")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("/mpk <message>").withStyle(ChatFormatting.AQUA))
                .append(" to talk to Pokemón.")
        )

        player.sendSystemMessage(
            Component.literal("Take a look at the cobblebrain guide or use\n")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("/cobblebrain guide").withStyle(ChatFormatting.AQUA))
        )
    }

    fun onChat(sender: ServerPlayer, rawContent: String) {
        if (!clientConfig.listenToChat) return

        if (clientConfig.onlyNearbyChat) {
            val nearbyPlayers = sender.server.playerList.players.filter {
                it.distanceTo(sender) <= 15.0
            }

            if (nearbyPlayers.isEmpty()) {
                sender.sendSystemMessage(Component.literal("Nenhum jogador próximo para ouvir sua fala."))
                return
            }

            nearbyPlayers.forEach { player ->
                val conteudo = if (player == sender) {
                    "The player (owner of the pokemon team) said to the pokemons: $rawContent"
                } else {
                    "${sender.name.string} said: $rawContent"
                }
                onPlayerChat(player, conteudo)
            }
        } else {
            sender.server.playerList.players.forEach { player ->
                val conteudo = if (player == sender) {
                    "The player (owner of the pokemon team) said to the pokemons: $rawContent"
                } else {
                    "${sender.name.string} said: $rawContent"
                }
                onPlayerChat(player, conteudo)
            }
        }
    }

    fun onDamage(entity: LivingEntity, source: DamageSource, amount: Float, newHealth: Float) {
        when (entity) {
            is ServerPlayer -> {
                val ativos = PokemonQuery.findActivePokemon(entity)
                if (ativos.isEmpty()) return

                val now = System.currentTimeMillis()
                val last = lastPrompt[entity.uuid] ?: 0L
                if (now - last >= 22000 && clientConfig.dialogueOnDamage) {
                    // limpa apenas a fila desse jogador
                    scheduledMessages[entity.uuid]?.clear()

                    lastPrompt[entity.uuid] = now
                    val cause = source.msgId
                    println("IMPORTANT: I took $amount of damage from $cause. Final health: $newHealth")
                    val prompt = buildPrompt(
                        entity,
                        ativos,
                        "IMPORTANT: I took $amount of damage from $cause. Final health: $newHealth"
                    )
                    sendToPlayer?.invoke(entity, prompt)
                }
            }

            is PokemonEntity -> {
                val ownerUuid = entity.pokemon.getOwnerUUID()
                if (ownerUuid != null && clientConfig.dialogueOnDamage) {
                    val server = entity.server ?: return
                    val owner: ServerPlayer? = server.playerList.getPlayer(ownerUuid)

                    if (owner != null) {
                        val ativos = PokemonQuery.findActivePokemon(owner)
                        if (ativos.isEmpty()) return

                        val now = System.currentTimeMillis()
                        val last = lastPrompt[owner.uuid] ?: 0L
                        if (now - last >= 22000) {
                            // limpa apenas a fila desse jogador
                            scheduledMessages[owner.uuid]?.clear()

                            lastPrompt[owner.uuid] = now
                            val cause = source.msgId
                            val pokemonNickname = entity.pokemon.nickname?.string
                            val pokemonSpecies = entity.pokemon.species.name
                            val pokemonName =
                                if (pokemonNickname.isNullOrBlank()) pokemonSpecies else pokemonNickname
                            println("My Pokémon $pokemonName took $amount of damage from $cause.")
                            val prompt = buildPrompt(
                                owner,
                                ativos,
                                "My Pokémon $pokemonName took $amount of damage from $cause."
                            )
                            sendToPlayer?.invoke(owner, prompt)
                        }
                    }
                }
            }
        }
    }

    fun onServerTick(server: MinecraftServer) {
        flushScheduledMessages(server)
        tickBubbles(currentServer!!)

        maintainLookAt(server)

        if (server.tickCount % 200 == 0) {
            for (player in server.playerList.players) {
                runSocialTick(player)
            }
        }

        if (server.tickCount % 40 == 0) {
            validateItemQuests(server)
        }
    }

    // 🔌 bridge de networking (Fabric vai implementar)
    var sendToPlayer: ((ServerPlayer, String) -> Unit)? = null
    var onSendPromptClient: (() -> Unit)? = null

    fun onBattleStarted(event: BattleStartedEvent) {
        val battle = event.battle
        val server = battle.players.firstOrNull()?.server ?: return

        if (!clientConfig.dialogueOnBattle) return

        server.execute {
            val ativos = battle.activePokemon.mapNotNull { it.battlePokemon }

            if (ativos.size < 2) {
                println("Battle started, but no active Pokémon detected yet.")
                return@execute
            }

            val meus = ativos.filter { it.originalPokemon.getOwnerUUID() != null }
            val inimigos = ativos.filter { it.originalPokemon.getOwnerUUID() == null }

            val player = meus.firstOrNull()?.originalPokemon?.let {
                val ownerId = it.getOwnerUUID() ?: return@execute
                server.playerList.getPlayer(ownerId)
            } ?: return@execute

            scheduledMessages[player.uuid]?.clear()

            val meusNomes = meus.joinToString(", ") {
                "${it.originalPokemon.species.name} (Lv.${it.originalPokemon.level})"
            }
            val inimigosNomes = inimigos.joinToString(", ") {
                "${it.originalPokemon.species.name} (Lv.${it.originalPokemon.level})"
            }

            val pokemonsTime = PokemonQuery.findActivePokemon(player)

            val prompt = buildPrompt(
                player,
                pokemonsTime,
                "IMPORTANT: I (the player) started a battle with my team[$meusNomes] against [$inimigosNomes]"
            )

            sendToPlayer?.invoke(player, prompt)
        }
    }

    fun onPokemonSent(event: PokemonSentEvent) {
        val pokemon = event.pokemon
        val ownerId = pokemon.getOwnerUUID() ?: return

        val battle = BattleRegistry.getBattleByParticipatingPlayerId(ownerId)
        if (battle != null && clientConfig.dialogueOnBattle) {
            val ownerPlayer = pokemon.getOwnerPlayer() ?: return

            scheduledMessages[ownerPlayer.uuid]?.clear()

            val nickname = pokemon.nickname
            val species = pokemon.species.name
            val level = pokemon.level

            val displayName = if (nickname != null) species else nickname ?: species

            val playerNameForPrompt =
                if (ownerPlayer == currentServer?.playerList?.getPlayer(ownerId)) "I"
                else ownerPlayer.name.string

            val ativos = PokemonQuery.findActivePokemon(ownerPlayer)

            val prompt = buildPrompt(
                ownerPlayer,
                ativos,
                "IMPORTANT: During the battle, $playerNameForPrompt sent $displayName (Lv.$level) to fight!"
            )

            sendToPlayer?.invoke(ownerPlayer, prompt)
        }
    }

    fun onBattleFled(event: BattleFledEvent) {
        val actor = event.player
        val uuids = actor.getPlayerUUIDs()

        if (!clientConfig.dialogueOnBattle) return
        if (uuids.isEmpty()) return

        val server = currentServer ?: return

        uuids.forEach { uuid ->
            val player = server.playerList.getPlayer(uuid) ?: return@forEach

            scheduledMessages[player.uuid]?.clear()

            val ativos = PokemonQuery.findActivePokemon(player)

            val prompt = buildPrompt(
                player,
                ativos,
                "IMPORTANT: we run away from the battle"
            )

            sendToPlayer?.invoke(player, prompt)
        }
    }

    fun onBattleVictory(event: BattleVictoryEvent) {
        val battle = event.battle
        if (!clientConfig.dialogueOnBattle) return

        for (player in battle.players) {
            val myActor = battle.actors.firstOrNull { it.uuid == player.uuid } ?: continue

            scheduledMessages[player.uuid]?.clear()

            if (event.winners.contains(myActor)) {
                for (loser in event.losers) {
                    val defeatedName = loser.getName().string
                    adjustKarma(player, defeatedName, -1)

                    val ativos = PokemonQuery.findActivePokemon(player)
                    val activeQuest = CobblebrainWorldSave.getActiveQuest(player)

                    if (activeQuest != null && activeQuest.get("type").asString == "BATTLE") {
                        val targetSpecies = activeQuest.get("targetSpecies").asString

                        if (defeatedName.equals(targetSpecies, ignoreCase = true)) {
                            val giverName =
                                CobblebrainWorldSave.getGiverNameFromQuest(activeQuest)

                            player.sendSystemMessage(
                                Component.literal("You completed the BATTLE quest. $giverName has something to say...")
                                    .withStyle(ChatFormatting.GREEN)
                            )

                            val prompt = buildPrompt(
                                player,
                                ativos,
                                "IMPORTANT: The player defeated the $targetSpecies! $giverName thanks him!"
                            )

                            sendToPlayer?.invoke(player, prompt)

                            adjustKarma(player, giverName, 2)
                            maybeGiveReward(player, giverName)
                        }
                    }
                }
            }
        }
    }

    fun onPokemonDeath(entity: PokemonEntity, killer: ServerPlayer) {
        val speciesName = entity.pokemon.species.name

        println("[DEBUG] ${killer.name.string} matou $speciesName")

        adjustKarma(killer, speciesName, -1)
        adjustKillCount(killer, speciesName, 1)
    }

    //fun ensureChatRunning() {
    //if (chatThread == null || !chatThread!!.isAlive) {
    //println("[CobbleBrain] AI not running, starting...")

    //chatThread = Thread {
    //try {
    //val chave = KeyManager.rotator.current()

    // Informational warning only — no hard stop
    //if (chave.isBlank()) {
    // println("[CobbleBrain] No API key configured. Assuming local / unauthenticated LLM.")
    //}

    //val chat = AIHandler("cobblebrain-ai")
    //chat.start()

    //} catch (e: Exception) {
    //e.printStackTrace()
    //}
    //}

    //chatThread!!.start()
    //}
    //}

    fun onPlayerChat(player: ServerPlayer, text: String) {
        scheduledMessages[player.uuid]?.clear()
        justSentMessage[player.uuid] = true
        val ativos = PokemonQuery.findActivePokemon(player)
        val prompt = buildPrompt(player, ativos, "\n\n$text")

        // envia prompt para o cliente processar
        sendToPlayer?.invoke(player, prompt)
    }


    // NÃO alterar o comportamento de tick/loop do flush
    private fun flushScheduledMessages(server: MinecraftServer) {
        val currentTick = server.tickCount.toLong()

        // percorre todos os jogadores online
        for (player in server.playerList.players) {
            val playerMessages = scheduledMessages[player.uuid] ?: continue
            val ready = playerMessages.filter { it.sendAtTick <= currentTick }

            if (ready.isNotEmpty()) {
                ready.forEach { msg ->
                    if (msg.text.startsWith("#") ||
                        (!config.showFriendship && msg.text.startsWith("friendship", ignoreCase = true))
                    ) {
                        return@forEach
                    }
                    println("=== DEBUG FLUSH ===")
                    println("msg.text='${msg.text}'")
                    println("msg.speaker='${msg.speaker}'")

                    if (clientConfig.dialogueInChat) {
                        val text = msg.text

                        // Regex para detectar "!Error 123!"
                        val regex = Regex("!Error \\d{3}!")

                        val component = if (regex.containsMatchIn(text)) {
                            // Mensagem inteira em vermelho
                            Component.literal(text).withStyle { style ->
                                style.withColor(ChatFormatting.RED)
                            }
                        } else {
                            // Mensagem normal
                            Component.literal(text)
                        }

                        player.sendSystemMessage(component)
                        println("[SENDING] Loop=${player.name.string} | MsgPlayer=${player.name.string}")
                    }

                    // tenta resolver o falante pelo apelido OU pela espécie
                    val ativos = PokemonQuery.findActivePokemon(msg.player)

                    // usa a função/variável que você já tem no collectWorldContext
                    val wildEntities = collectWorldContext(msg.player).nearbyPokemonEntities
                    val wilds = wildEntities.map { it.pokemon }

                    // junta os dois conjuntos
                    val participantes = ativos + wilds

                    participantes.forEach { poke ->
                        println("participante nickname='${poke.nickname}' especie='${poke.species.resourceIdentifier.path}'")
                    }

                    val rawName = msg.text.substringBefore(":").trim()

                    val speaker = msg.speaker ?: participantes.find { poke ->
                        val nick = poke.nickname?.string
                        nick?.equals(rawName, ignoreCase = true) == true ||
                                poke.species.resourceIdentifier.path.equals(rawName, ignoreCase = true)
                    }

                    println("speaker resolvido = ${speaker?.nickname ?: speaker?.species?.resourceIdentifier?.path ?: "null"}")

                    speaker?.let { pokemon ->
                        val entity = pokemon.entity
                        val basePitch = entity?.uuid?.let { pokemonPitchMap[it] } ?: 1.0f
                        expressPokemon(pokemon, basePitch)

                        if (entity != null && clientConfig.chatbubbles) {
                            val bubbleText = msg.text.substringAfter(":").trim()
                            spawnSpeechBubble(server, pokemon, bubbleText, 100)
                        }

                        // define foco social e espectadores
                        currentSpeaker = pokemon
                        speakerUntilTick = server.tickCount.toLong() + 100 // ~5s
                        currentViewers.clear()

                        // espectadores: pokémon ativos do mesmo player, exceto o falante
                        ativos.filter { other -> other != pokemon }.forEach { other ->
                            currentViewers.add(other)
                            val otherEntity = other.entity
                            val speakerEntity = pokemon.entity
                            if (otherEntity != null && speakerEntity != null) {
                                otherEntity.lookControl.setLookAt(
                                    speakerEntity.x,
                                    speakerEntity.eyeY,
                                    speakerEntity.z,
                                    30f,
                                    30f
                                )
                            }
                        }
                    }
                }
                playerMessages.removeAll(ready)
            }
        }
    }


    // mapa para controlar quando limpar cada bolha
    private val bubbleUntilTick = mutableMapOf<UUID, Long>()
    private val bubbleStands = mutableMapOf<UUID, UUID>()

    fun getBubbleY(entity: Entity): Double {
        // topo da hitbox + pequeno offset
        return entity.eyeY - 1
    }

    fun spawnSpeechBubble(
        server: MinecraftServer,
        pokemon: Pokemon,
        text: String,
        durationTicks: Int = 60,
        charsPerTick: Int = 1
    ) {
        val entity = pokemon.entity ?: return
        val level = entity.level()

        val stand = ArmorStand(EntityType.ARMOR_STAND, level)
        stand.isInvisible = true
        stand.isNoGravity = true
        stand.isCustomNameVisible = true
        stand.addTag("Marker")
        stand.moveTo(entity.x, getBubbleY(entity), entity.z)

        // começa vazio (ou com um cursor, se quiser)
        stand.customName = Component.literal("")

        level.addFreshEntity(stand)

        bubbleUntilTick[stand.uuid] = server.tickCount.toLong() + durationTicks
        bubbleStands[entity.uuid] = stand.uuid

        bubbleText[stand.uuid] = text
        bubbleProgress[stand.uuid] = 0
        bubbleSpeed[stand.uuid] = charsPerTick
    }


    fun tickBubbles(server: MinecraftServer) {
        val current = server.tickCount.toLong()
        val expired = bubbleUntilTick.filterValues { it <= current }.keys

        // remove stands expirados
        expired.forEach { standUuid ->
            server.allLevels.forEach { level ->
                val stand = level.getEntity(standUuid)
                if (stand is ArmorStand) {
                    stand.discard()
                }
            }
            bubbleUntilTick.remove(standUuid)
            bubbleStands.entries.removeIf { it.value == standUuid }
        }

        // atualizar posição dos stands ativos para seguir o pokémon
        val toRemove = mutableListOf<UUID>()

        bubbleStands.forEach { (pokemonUuid, standUuid) ->
            var pokeFound = false
            server.allLevels.forEach { level ->
                val poke = level.getEntity(pokemonUuid)
                val stand = level.getEntity(standUuid)

                if (poke != null && stand is ArmorStand) {
                    pokeFound = true
                    stand.moveTo(poke.x, getBubbleY(poke), poke.z)
                }
            }

            // avançar texto tipo "typewriter"
            bubbleProgress.keys.forEach { standUuid ->
                val text = bubbleText[standUuid] ?: return@forEach
                val speed = bubbleSpeed[standUuid] ?: 1
                val current = bubbleProgress[standUuid] ?: 0

                val newProgress = (current + speed).coerceAtMost(text.length)
                bubbleProgress[standUuid] = newProgress

                // atualizar nome visível
                server.allLevels.forEach { level ->
                    val stand = level.getEntity(standUuid)
                    if (stand is ArmorStand) {
                        val shown = text.take(newProgress)
                        stand.customName = Component.literal(shown)
                    }
                }
            }


            // se o pokémon não existe mais, remove o stand
            if (!pokeFound) {
                server.allLevels.forEach { level ->
                    val stand = level.getEntity(standUuid)
                    if (stand is ArmorStand) {
                        stand.discard()
                    }
                }
                toRemove.add(pokemonUuid)
                bubbleUntilTick.remove(standUuid)
            }
        }

        // limpa vínculos órfãos
        toRemove.forEach { bubbleStands.remove(it) }
    }

    // ---------------         QUEST SECTION            -----------------------
    fun canBetray(player: ServerPlayer, giver: PokemonEntity): Boolean {
        val playerHealth = player.health
        val playerArmor = player.armorValue // total de pontos de armadura

        val giverHealth = giver.health
        val physicalAttack = giver.pokemon.attack
        val specialAttack = giver.pokemon.specialAttack
        val giverDamage = maxOf(physicalAttack, specialAttack) / 10

        // compara vida+armadura do player com vida+dano do Pokémon
        return (playerHealth + playerArmor) <= (giverHealth + giverDamage * 1.2)
    }

    fun maybeGiveReward(player: ServerPlayer, giverName: String) {
        val karmaRoot = CobblebrainWorldSave.data.getAsJsonObject("karma")
        val playerKey = player.uuid.toString()

        val current = if (karmaRoot.has(playerKey)) {
            karmaRoot.getAsJsonObject(playerKey).get(giverName)?.asInt ?: 0
        } else 0

        val minKarma = 6
        val maxKarma = 20
        val baseChance = 0.4f

        val chance = when {
            current >= maxKarma -> 1.0f
            current >= minKarma ->
                baseChance + ((current - minKarma).toFloat() / (maxKarma - minKarma)) * (1.0f - baseChance)
            else -> 0f
        }

        if (player.server.overworld().random.nextFloat() < chance) {
            val possibleRewards = listOf(
                ItemStack(Items.SWEET_BERRIES, 9),
                ItemStack(CobblemonItems.EXPERIENCE_CANDY_S, 3),
                ItemStack(CobblemonItems.EXPERIENCE_CANDY_M, 2),
                ItemStack(CobblemonItems.REVIVE, 1),
                ItemStack(CobblemonItems.FRIEND_BALL, 4),
                ItemStack(CobblemonItems.RELIC_COIN_SACK, 1)
            )
            val rewardItem = possibleRewards.random(player.server.overworld().random as Random)

            val count = rewardItem.count
            val itemNameComponent = rewardItem.hoverName.copy()

            if (!player.inventory.add(rewardItem)) {
                player.drop(rewardItem, false)
            }

            player.sendSystemMessage(
                Component.literal("$giverName gave you ")
                    .append(itemNameComponent)
                    .append(" x$count as a gift!")
                    .withStyle(ChatFormatting.GREEN)
            )
        }
    }

    fun validateQuestGiversOnPlayerJoin(server: MinecraftServer, player: ServerPlayer) {
        val level = server.overworld()
        val data = CobblebrainWorldSave.data
        if (!data.has("quests")) return

        val questsRoot = data.getAsJsonObject("quests")
        if (!questsRoot.has("active")) return

        val activeArray = questsRoot.getAsJsonArray("active")

        val abandonedArray = if (questsRoot.has("abandoned")) {
            questsRoot.getAsJsonArray("abandoned")
        } else {
            val newArray = com.google.gson.JsonArray()
            questsRoot.add("abandoned", newArray)
            newArray
        }

        val playerChunk = player.chunkPosition()
        val radius = 3 // leve e multiplayer safe (eu espero...)

        val iterator = activeArray.iterator()
        while (iterator.hasNext()) {
            val questObj = iterator.next().asJsonObject

            if (!questObj.has("status") ||
                !questObj.has("ownerUuid") ||
                !questObj.has("giverUuid")
            ) continue

            if (questObj.get("status").asString != "IN_PROGRESS") continue
            if (questObj.get("ownerUuid").asString != player.uuid.toString()) continue

            val giverUuid = try {
                UUID.fromString(questObj.get("giverUuid").asString)
            } catch (e: Exception) {
                continue
            }
            val giverEntity = level.getEntity(giverUuid)

            if (giverEntity is PokemonEntity) {
                val giverChunk = giverEntity.chunkPosition()
                val dx = giverChunk.x - playerChunk.x
                val dz = giverChunk.z - playerChunk.z

                val withinRange = kotlin.math.abs(dx) <= radius &&
                        kotlin.math.abs(dz) <= radius

                val chunkLoaded = level.hasChunk(giverChunk.x, giverChunk.z)

                if (withinRange && chunkLoaded) {
                    val giverName =
                        giverEntity.pokemon.nickname?.string
                            ?: giverEntity.pokemon.species.resourceIdentifier.path
                    player.sendSystemMessage(
                        Component.literal("A quest de $giverName ainda está ativa!")
                            .withStyle(ChatFormatting.GREEN)
                    )
                    continue
                }
            }

            // Não encontrado ou fora da área
            iterator.remove()

            questObj.addProperty("status", "ENF")
            abandonedArray.add(questObj)
            player.sendSystemMessage(
                Component.literal("Uma quest foi abandonada porque o pokémon que a solicitou não está mais aqui... (Entity Not Found).")
                    .withStyle(ChatFormatting.YELLOW)
            )
        }

        CobblebrainWorldSave.save()
    }

    fun validateItemQuests(server: MinecraftServer) {
        val level = server.overworld()

        val activeArray = CobblebrainWorldSave.data
            .getAsJsonObject("quests")
            .getAsJsonArray("active")

        // Faz cópia para evitar modificar enquanto itera
        val quests = activeArray
            .map { it.asJsonObject.deepCopy() }
            .filter {
                it.get("type").asString == "ITEM" &&
                        it.get("status").asString == "IN_PROGRESS"
            }

        quests.forEach { questObj ->

            val giverUuid = UUID.fromString(questObj.get("giverUuid").asString)
            val ownerUuid = UUID.fromString(questObj.get("ownerUuid").asString)

            val target = questObj.get("target").asString
            val amount = questObj.get("amount").asInt

            val giverEntity = level.getEntity(giverUuid) as? PokemonEntity ?: return@forEach
            val player = server.playerList.getPlayer(ownerUuid) ?: return@forEach

            val nearbyItems = level.getEntitiesOfClass(
                ItemEntity::class.java,
                giverEntity.boundingBox.inflate(5.0)
            )

            val collected = nearbyItems
                .filter { BuiltInRegistries.ITEM.getKey(it.item.item).path == target }
                .sumOf { it.item.count }

            if (collected >= amount) {
                var remaining = amount
                val matchingItems = nearbyItems
                    .filter { BuiltInRegistries.ITEM.getKey(it.item.item).path == target }

                for (itemEntity in matchingItems) {
                    if (remaining <= 0) break

                    val stack = itemEntity.item
                    val removeAmount = minOf(stack.count, remaining)

                    stack.shrink(removeAmount)
                    remaining -= removeAmount

                    if (stack.isEmpty) {
                        itemEntity.discard()
                    }
                }

                println("[DEBUG] Removed $amount $target from ground")

                // Sons de armazenamento
                player.level().playSound(
                    null,
                    giverEntity.blockPosition(),
                    SoundEvents.ITEM_PICKUP,
                    SoundSource.PLAYERS,
                    0.8f,
                    0.9f
                )

                player.level().playSound(
                    null,
                    giverEntity.blockPosition(),
                    SoundEvents.CHEST_CLOSE,
                    SoundSource.PLAYERS,
                    0.5f,
                    1.1f
                )

                CobblebrainWorldSave.moveQuest(
                    ownerUuid.toString(),
                    giverUuid.toString(),
                    "ITEM",
                    "COMPLETED"
                )

                val giverName =
                    giverEntity.pokemon.nickname?.string
                        ?: giverEntity.pokemon.species.resourceIdentifier.path

                val karma = CobblebrainWorldSave.data.getAsJsonObject("karma")
                val current = karma.get(giverName)?.asInt ?: 0
                karma.addProperty(giverName, current + 1)
                CobblebrainWorldSave.save()

                buildPrompt(
                    player,
                    PokemonQuery.findActivePokemon(player),
                    "IMPORTANT: Mission concluded! $giverName thanks the player for bringing the $amount $target(s)!"
                )

                println("[DEBUG] Karma atualizado para $giverName: ${current + 1}")
                player.sendSystemMessage(
                    Component.literal("You completed the ITEM quest. $giverName has something to say...")
                        .withStyle(ChatFormatting.GREEN)
                )
            }
        }
    }

    fun handleAdviceQuestResponse(player: ServerPlayer, giver: PokemonEntity?, response: String) {
        println("[DEBUG] Entrou em handleAdviceQuestResponse")
        println("[DEBUG] Resposta recebida: $response")

        val quest = CobblebrainWorldSave.getActiveQuest(player)
        if (quest == null) {
            println("[DEBUG] Nenhuma quest ativa encontrada")
            return
        }

        val giverUuid = quest.get("giverUuid").asString
        val giverName = giver?.pokemon?.nickname?.string
            ?: giver?.pokemon?.species?.resourceIdentifier?.path
            ?: CobblebrainWorldSave.getGiverNameFromQuest(quest)

        val endTagRegex = Regex(
            """%\s*[:\-]?\s*(positive|negative|betray|leave)[\s_\-]*end""",
            RegexOption.IGNORE_CASE
        )

        val match = endTagRegex.find(response)
        val tag = match?.groupValues?.get(1)?.lowercase()

        when (tag) {

            "positive" -> {
                println("[DEBUG] Detectado %positive_end")
                player.sendSystemMessage(
                    Component.literal("$giverName appreciated your help!")
                        .withStyle(ChatFormatting.GREEN)
                )

                CobblebrainWorldSave.moveQuest(
                    player.uuid.toString(),
                    giverUuid,
                    quest.get("type").asString,
                    "COMPLETED"
                )
                adjustKarma(player,giverName, +2)
                CobblebrainWorldSave.debugQuests()
                maybeGiveReward(player, giverName)
            }

            "negative" -> {
                println("[DEBUG] Detectado %negative_end")
                player.sendSystemMessage(
                    Component.literal("$giverName didn't like your help!")
                        .withStyle(ChatFormatting.RED)
                )

                CobblebrainWorldSave.moveQuest(
                    player.uuid.toString(),
                    giverUuid,
                    quest.get("type").asString,
                    "COMPLETED"
                )
                adjustKarma(player,giverName, -1)
                CobblebrainWorldSave.debugQuests()
            }

            "betray" -> {
                println("[DEBUG] Detectado %betray_end")
                player.sendSystemMessage(
                    Component.literal("$giverName betrayed you!")
                        .withStyle(ChatFormatting.RED)
                )

                if (giver != null && canBetray(player, giver)) {
                    CobblebrainWorldSave.moveQuest(
                        player.uuid.toString(),
                        giverUuid,
                        quest.get("type").asString,
                        "ABANDONED"
                    )
                    adjustKarma(player,giverName, -2)
                    CobblebrainWorldSave.debugQuests()

                    val chosenAttack = maxOf(giver.pokemon.attack, giver.pokemon.specialAttack)
                    giver.getAttribute(Attributes.ATTACK_DAMAGE)?.baseValue =
                        chosenAttack.toDouble() / 20

                    giver.target = player
                    giver.isAggressive = true
                    println("[DEBUG] Pokémon ficou agressivo contra o player")
                }
            }

            "leave" -> {
                println("[DEBUG] Detectado %leave_end")
                player.sendSystemMessage(
                    Component.literal("$giverName decided to abandon the quest!")
                        .withStyle(ChatFormatting.YELLOW)
                )

                CobblebrainWorldSave.moveQuest(
                    player.uuid.toString(),
                    giverUuid,
                    quest.get("type").asString,
                    "ABANDONED"
                )
                CobblebrainWorldSave.debugQuests()

                if (giver != null) {
                    val dx = giver.x - player.x
                    val dz = giver.z - player.z
                    val dist = sqrt(dx * dx + dz * dz)

                    if (dist > 0) {
                        val awayX = giver.x + (dx / dist) * 10.0
                        val awayZ = giver.z + (dz / dist) * 10.0
                        giver.navigation.moveTo(awayX, giver.y, awayZ, 1.0)
                        println("[DEBUG] Pokémon se afastou do player")
                    }
                }

            }

            else -> {
                println("[DEBUG] Nenhum marcador detectado nesta resposta")
            }
        }
    }

    // reaplica o olhar todos os ticks enquanto durar o foco
    private fun maintainLookAt(server: MinecraftServer) {
        val now = server.tickCount.toLong()
        val speakerEntity = currentSpeaker?.entity
        if (speakerEntity == null || now > speakerUntilTick) {
            // encerra foco
            currentSpeaker = null
            currentViewers.clear()
            return
        }

        currentViewers.forEach { viewer ->
            val viewerEntity = viewer.entity ?: return@forEach

            // calcula ângulo entre viewer e speaker
            val dx = speakerEntity.x - viewerEntity.x
            val dz = speakerEntity.z - viewerEntity.z
            val angle = (toDegrees(atan2(dz, dx)) - 90).toFloat()

            // só gira a cabeça, corpo continua livre
            viewerEntity.yHeadRot = angle
            viewerEntity.yHeadRotO = angle
        }
    }

    private fun runSocialTick(player: ServerPlayer) {
        // Se esse jogador acabou de mandar mensagem, não dispara espontâneo neste tick
        if (justSentMessage[player.uuid] == true) {
            justSentMessage[player.uuid] = false
            println("[DEBUG] Bloqueando diálogo espontâneo para ${player.name.string} porque ele acabou de falar")
            return
        }

        // Se esse jogador tem mensagens pendentes, não dispara espontâneo para ele
        val playerMessages = scheduledMessages[player.uuid] ?: emptyList()
        if (playerMessages.isNotEmpty()) {
            println("[DEBUG] Jogador ${player.name.string} tem mensagens pendentes, não disparar espontâneo")
            return
        }

        val ativos = PokemonQuery.findActivePokemon(player)
        if (ativos.isEmpty()) return

        val chance = clientConfig.spontaneousDialogueChance

        // Sorteio para disparar diálogo espontâneo só para esse jogador
        if (Random.nextDouble() <= chance) {
            player.sendSystemMessage(
                Component.literal("Your team is thinking about something to say...")
                    .withStyle(ChatFormatting.YELLOW)
            )
            val prompt = buildPrompt(
                player,
                ativos,
                "IMPORTANT: The Pokémon are thinking of something different to say..."
            )

            sendToPlayer?.invoke(player, prompt)
            println("[DEBUG] Spontaneous dialogue triggered for ${player.name.string}")
        }
    }


    fun playPokemonCry(pokemon: Pokemon, pitch: Float = 1.0f) {
        val entity = pokemon.entity ?: return
        val level = entity.level() as? ServerLevel ?: return
        val pos = entity.blockPosition()

        val cryId = ResourceLocation.fromNamespaceAndPath(
            "cobblemon",
            "pokemon.${pokemon.species.resourceIdentifier.path}.cry"
        )
        val cry = SoundEvent.createVariableRangeEvent(cryId)

        level.playSound(
            null,
            pos,
            cry,
            SoundSource.NEUTRAL,
            1.0f,
            pitch
        )
    }


    fun expressPokemon(pokemon: Pokemon, basePitch: Float = 1.0f) {
        val entity = pokemon.entity ?: return
        val level = entity.level() as? ServerLevel ?: return

        println(pokemon)

        // aplica variação pequena em torno do pitch base
        val variedPitch = (basePitch + (Random.nextFloat() * 0.05f - 0.10f))
            .coerceIn(0.5f, 1.5f)

        // toca o cry com pitch variado
        playPokemonCry(pokemon, variedPitch)

        // só pula se estiver no chão
        if (entity.onGround()) {
            entity.jumpFromGround()
        }

        // partículas de acordo com o pitch base (não variado)
        val particleType = if (basePitch >= 1.0f) ParticleTypes.HEART else ParticleTypes.ANGRY_VILLAGER
        level.sendParticles(
            particleType,
            entity.x, entity.y + entity.bbHeight / 2.0, entity.z,
            6,
            0.25, 0.35, 0.25,
            0.0
        )
    }

    fun buildPrompt(player: ServerPlayer, pokemons: List<Pokemon>, moreText: String): String {
        val context = collectWorldContext(player)

        return buildString {
            appendLine(moreText)
            appendLine()
            // Environment
            appendLine("Biome: ${context.biome}")
            appendLine("Weather: ${context.weather}")
            appendLine("Time: ${context.timeOfDay}, ${context.timeLabel})")

            if (!clientConfig.lowTokenMode) {
                appendLine("Light: ${context.lightLevel}")
                appendLine("Block under the player's feet: ${context.blockUnder}")
                appendLine("Nearby special blocks: ${context.specialBlocks}")
            }

            if (!clientConfig.lowTokenMode) {
                appendLine("Nearby entities: ${context.nearbyEntities}")
                appendLine("Nearby mobs: ${context.nearbyMobs}")
                appendLine("Nearby pokemons (not on the team): ${context.nearbyPokemon}")
                println("Nearby pokemons (not on the team): ${context.nearbyPokemon}")
            }
            appendLine("Items on the ground: ${context.nearbyItems}")

            appendLine("Player health: ${context.health}/${context.maxHealth}")
            appendLine("Player's main hand: ${context.mainHand}")
            appendLine()

            if (config.wildPokemonTalkChance > 0.0
                && Random.nextDouble() <= config.wildPokemonTalkChance
                && context.nearbyPokemonEntities.isNotEmpty()
            ) {
                val wildEntity = context.nearbyPokemonEntities.randomOrNull()
                if (wildEntity != null) {
                    val giver = wildEntity.pokemon

                    val questsRoot = CobblebrainWorldSave.data.getAsJsonObject("quests")
                    val activeArray = questsRoot.getAsJsonArray("active")

                    val hasActiveQuest = activeArray.any {
                        val obj = it.asJsonObject
                        obj.get("ownerUuid").asString == player.uuid.toString() &&
                                obj.get("status").asString == "IN_PROGRESS"
                    }

                    // Se não há missão ativa
                    if (!hasActiveQuest && Random.nextDouble() <= config.wildQuestChance) {
                        val roll = Random.nextInt(3) // 0 = Advice, 1 = Item, 2 = Battle
                        when (roll) {
                            0 -> {
                                CobblebrainWorldSave.createAdviceQuest(player, wildEntity)
                                appendLine("IMPORTANT: ${giver.nickname?.string ?: giver.species.resourceIdentifier.path} has started an ADVICE quest! It wants to talk with the player or their Pokémon team!")
                                player.sendSystemMessage(
                                    Component.literal(
                                        "${giver.nickname?.string ?: giver.species.resourceIdentifier.path} has started an ADVICE quest!"
                                    ).withStyle(ChatFormatting.YELLOW)
                                )
                            }

                            1 -> {
                                CobblebrainWorldSave.createItemQuest(player, wildEntity)
                                val activeQuests =
                                    CobblebrainWorldSave.data.getAsJsonObject("quests").getAsJsonArray("active")
                                val itemQuest = activeQuests.last().asJsonObject
                                val targetItem = itemQuest.get("target").asString
                                val amount = itemQuest.get("amount").asInt
                                appendLine("IMPORTANT: ${giver.nickname?.string ?: giver.species.resourceIdentifier.path} has started an ITEM quest! It needs the player or their Pokémon team to gather x$amount $targetItem! It wants to talk with the player or their Pokémon team!")
                                player.sendSystemMessage(
                                    Component.literal(
                                        "${giver.nickname?.string ?: giver.species.resourceIdentifier.path} has started an ITEM quest!"
                                    ).withStyle(ChatFormatting.YELLOW)
                                )
                            }

                            2 -> {
                                CobblebrainWorldSave.createBattleQuest(player, wildEntity)
                                val activeQuests =
                                    CobblebrainWorldSave.data.getAsJsonObject("quests").getAsJsonArray("active")
                                val battleQuest = activeQuests.last().asJsonObject
                                val targetSpecies = battleQuest.get("targetSpecies").asString
                                appendLine("IMPORTANT: ${giver.nickname?.string ?: giver.species.resourceIdentifier.path} has started a BATTLE quest! It wants the player or their Pokémon team to defeat a $targetSpecies in a pokemon battle! It wants to talk with the player or their Pokémon team!")
                                player.sendSystemMessage(
                                    Component.literal(
                                        "${giver.nickname?.string ?: giver.species.resourceIdentifier.path} has started an BATTLE quest!"
                                    ).withStyle(ChatFormatting.YELLOW)
                                )
                            }
                        }
                    } else if (!hasActiveQuest) {
                        appendLine("IMPORTANT: Wild Nearby pokemons (not on the team) are talking about something!")
                    }
                }
            }

            appendLine("[Active pokemons]")

            pokemons.forEach { p ->
                val allMoves: List<String> = p.moveSet.getMoves().map { it.name }
                appendLine("Nickname: ${p.nickname?.string} | Species: ${p.species.name} | UUID: ${p.uuid} | HP: ${p.currentHealth}/${p.maxHealth} | Lvl: ${p.level} | Nature: ${p.effectiveNature.name} | Moveset: $allMoves | Friendship with player: ${p.friendship} | Fainted: ${p.isFainted()} | Is flying: ${p.entity?.isPokemonFlying} | Is player mounted: ${p.entity!!.passengers.any { it is ServerPlayer }}")

                // Adiciona características se houver
                val nameToCheck = p.nickname?.string ?: p.species.name
                clientConfig.characteristics.forEach { entry ->
                    val parts = entry.split(":")
                    if (parts.size >= 2) {
                        val charName = parts[0].trim()
                        val charDesc = parts.drop(1).joinToString(":").trim()
                        if (nameToCheck.equals(charName, ignoreCase = true)) {
                            appendLine("Characteristics of $nameToCheck: $charDesc")
                        }
                    }
                }
                val speciesName = p.species.name

                if (player.stringUUID != null) {
                    // Karma
                    val karmaRoot = CobblebrainWorldSave.data.getAsJsonObject("karma")
                    if (karmaRoot.has(player.stringUUID)) {
                        val playerKarma = karmaRoot.getAsJsonObject(player.stringUUID)
                        if (playerKarma.has(speciesName)) {
                            val karmaValue = playerKarma.get(speciesName).asInt
                            appendLine("Karma of $speciesName for the player: $karmaValue")
                        }
                    }

                    val killRoot = CobblebrainWorldSave.data.getAsJsonObject("kill_count")
                    if (killRoot.has(player.stringUUID)) {
                        val playerKills = killRoot.getAsJsonObject(player.stringUUID)

                        // Mostrar todas as kills do jogador
                        playerKills.entrySet().forEach { entry ->
                            val species = entry.key
                            val killValue = entry.value.asInt
                            appendLine("The player killed x$killValue $species")
                        }

                        // Resetar o kill count do jogador
                        killRoot.add(player.stringUUID, JsonObject())
                        CobblebrainWorldSave.save()
                    }
                }

                val memories = currentServer?.let { srv ->
                    loadPokemonMemories(
                        srv,
                        p.uuid.toString(),
                        clientConfig.maxShortMemory
                    )
                } ?: emptyList()

                if (memories.isNotEmpty()) {
                    appendLine("\nMemories:\n")
                    memories.forEach { m ->
                        appendLine("@Pokemon ${p.nickname?.string ?: p.species.name}: $m\n")
                    }
                }
            }
            appendLine("Important variables:")
            appendLine("AFFECT_FRIENDSHIP_PLUS: ${config.increaseFriendship}")
            appendLine("AFFECT_FRIENDSHIP_MINUS: ${config.decreaseFriendship}")
            appendLine("Send the entire response in ${clientConfig.selectedLanguage}")
        }.trim()
    }

    private val lastResponseContent = mutableMapOf<UUID, String>()

    fun checkIaResponse(server: MinecraftServer, player: ServerPlayer, content: String) {
        val last = lastResponseContent[player.uuid]
        if (content.isBlank() || content == last) return

        // Linhas para chat (falas + friendship), excluindo memórias
        val allLines = content.split("|")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val falas = allLines.filterNot {
            it.startsWith("@") ||
                    it.startsWith("#") ||
                    it.startsWith("&") ||
                    it.startsWith("%") ||
                    (!config.showFriendship && it.startsWith("friendship", ignoreCase = true))
        }

        val commandLines = allLines.filter { it.startsWith("#") }
        val summaryLines = allLines.filter { it.startsWith("&", ignoreCase = true) }


        val memoryLines = content.split("|")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filter { it.startsWith("@") }

        // 1. Salva as memórias no JSON
        memoryLines.forEach { line ->
            savePokemonMemory(server, line, clientConfig.maxShortMemory)
        }

        // 2. Detecta quest summary
        summaryLines.forEach { line ->

        val summaryText = line
            .substringAfter("&")
            .removePrefix(":")
            .trim()

        if (summaryText.isBlank()) return@forEach

        val activeArray = CobblebrainWorldSave.data
            .getAsJsonObject("quests")
            .getAsJsonArray("active")

        val lastQuest = activeArray
            .map { it.asJsonObject }
            .asReversed()
            .firstOrNull {
                it.get("status")?.asString == "IN_PROGRESS" &&
                        it.get("ownerUuid")?.asString == player.uuid.toString()
            }

        if (lastQuest != null) {
            lastQuest.addProperty("questSummary", summaryText)
            CobblebrainWorldSave.save()}
        }

        // 3. Detecta ações (#)
        commandLines.forEach { line ->
            val cmd = parseCommand(line)
            if (cmd != null) {
                val level = player.level() as ServerLevel
                val pokemon = level.getEntitiesOfClass(Mob::class.java, player.boundingBox.inflate(64.0)) {
                    it.displayName?.string.equals(cmd.pokemonName, ignoreCase = true)
                }.firstOrNull()

                if (pokemon != null) {
                    CommandState.activeCommands[pokemon.uuid] = cmd.action
                }
            }
        }

        // 4. Friendship updates
        val ativos = server.playerList.players.flatMap { PokemonQuery.findActivePokemon(it) }
        val regex = Regex(
            """friendship\s+([\w\s.'♀♂-]+):\s*([\d.,]+)\s*([+-])\s*(-?\d+)""",
            RegexOption.IGNORE_CASE
        )
        val matches = regex.findAll(content)
        for (match in matches) {
            val nomePokemon = match.groupValues[1]
            //val atual = match.groupValues[2].toDouble()
            val sinal = match.groupValues[3]
            val incrementoValor = match.groupValues[4].toDouble()
            val incremento = if (sinal == "-") -incrementoValor else incrementoValor

            val alvo = ativos.firstOrNull { ativo ->
                val nomeNormalizado = nomePokemon.trim()
                val nickname = ativo.nickname?.string
                nickname?.equals(nomeNormalizado, ignoreCase = true)
                    ?: ativo.species.name.equals(nomeNormalizado, ignoreCase = true)
            }

            if (alvo != null) {
                val incrementoInt = incremento.toInt()
                alvo.entity?.let {
                    if (incremento > 0 && config.increaseFriendship) {
                        alvo.incrementFriendship(incrementoInt)
                    }
                    if (incremento < 0 && config.decreaseFriendship) {
                        alvo.decrementFriendship(incrementoInt)
                    }
                }
            }
        }

        // 5. Agenda mensagens para o jogador que falou
        lastResponseContent[player.uuid] = content
        val startTick = server.tickCount.toLong()

        // Monta todas as falas do novo diálogo
        val novasMensagens = falas.mapIndexed { i, line ->
            val speakerName = line.substringBefore(":").trim()
            val speaker = PokemonQuery.findActivePokemon(player)
                .firstOrNull { it.species.name.equals(speakerName, ignoreCase = true) }

            // 6. Detecta marcadores (%POSITIVE_END, etc.)
            if (line.startsWith("%")) {
                println("[DEBUG] Marcador detectado na fala: $line")
                handleAdviceQuestResponse(player, speaker?.entity, line)
            }

            ScheduledMessage(
                player = player,
                text = line,
                sendAtTick = if (i == 0) startTick else startTick + (i * 100),
                speaker = speaker
            )
        }

        // Substitui qualquer diálogo anterior por este novo conjunto
        scheduledMessages[player.uuid] = novasMensagens.toMutableList()
        println("[SCHEDULE] ${player.name.string} -> ${novasMensagens.size} mensagens")

    }
}