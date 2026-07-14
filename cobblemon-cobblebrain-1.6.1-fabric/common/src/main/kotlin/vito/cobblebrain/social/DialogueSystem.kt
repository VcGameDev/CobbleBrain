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
import com.google.gson.JsonArray
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
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.Vec3
import vito.cobblebrain.social.CobblebrainWorldSave.adjustKarma
import vito.cobblebrain.social.CobblebrainWorldSave.adjustKillCount
import vito.cobblebrain.config.ConfigHandler.config
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import vito.cobblebrain.currentServer
import vito.cobblebrain.sensors.CommandState
import vito.cobblebrain.sensors.collectWorldContext
import vito.cobblebrain.sensors.parseCommand
import java.lang.Math.toDegrees
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.atan2

object PlayerTopicState {
    private val topics = mutableMapOf<UUID, String>()

    fun get(playerId: UUID): String? {
        return topics[playerId]
    }

    fun set(playerId: UUID, topic: String) {
        topics[playerId] = topic
    }
}

object PlayerConversationState {
    private val lastInteraction = mutableMapOf<UUID, Long>()

    fun update(playerId: UUID, tick: Long) {
        lastInteraction[playerId] = tick
    }

    fun get(playerId: UUID): Long? {
        return lastInteraction[playerId]
    }
}

object DialogueSystem {
    val justSentMessage: MutableMap<UUID, Boolean> = ConcurrentHashMap()
    val lastPlayerMessage: MutableMap<UUID, String> = ConcurrentHashMap()
    private val lastResponseContent = mutableMapOf<UUID, String>()
    private val pendingBarrelRemovals = mutableMapOf<net.minecraft.core.GlobalPos, Long>() // Pos -> Tick que foi marcado
    private val pendingQuestNote = mutableMapOf<UUID, String>()
    private val isWaitingForQuestResponse = mutableMapOf<UUID, Boolean>()
    private val pendingInterruption = mutableMapOf<UUID, Boolean>()
    private val questResponseTimeout = mutableMapOf<UUID, Long>() // Player -> Tick limite
    private val serverLastInteractions = ConcurrentHashMap<UUID, MutableList<String>>()

    val scheduledMessages: MutableMap<UUID, MutableList<ScheduledMessage>> = ConcurrentHashMap()

    private val pokemonAliasMap =
        mutableMapOf<UUID, MutableMap<String, UUID>>()
    // playerUUID -> (alias -> pokemonUUID)

    private val reversePokemonAliasMap =
        mutableMapOf<UUID, MutableMap<UUID, String>>()
    // playerUUID -> (pokemonUUID -> alias)

    data class ScheduledMessage(
        val player: ServerPlayer,
        val text: String,
        val sendAtTick: Long,
        val speaker: Pokemon? = null,
        val pitchMod: Float = 0f
    )

    data class TemporaryFeedback(
        val text: String,
        var remainingUses: Int = 3
    )

    private val temporaryFeedback =
        mutableMapOf<UUID, MutableList<TemporaryFeedback>>()

    fun addFeedback(
        player: ServerPlayer,
        feedback: String
    ) {
        temporaryFeedback
            .getOrPut(player.uuid) { mutableListOf() }
            .add(
                TemporaryFeedback(feedback)
            )
    }

    // Estado social para manter o olhar
    private var currentSpeaker: Pokemon? = null
    private var speakerUntilTick: Long = 0L
    private val currentViewers = mutableListOf<Pokemon>()

    // guarda o último momento em que cada jogador disparou a lógica
    private val lastPrompt: MutableMap<UUID, Long> = ConcurrentHashMap()

    private fun giveQuestXp(player: ServerPlayer, quest: JsonObject, battleTargetLevel: Int = 0) {
        val type = quest.get("type")?.asString ?: return
        val xp = when (type) {
            "ITEM" -> {
                val amount = quest.get("amount")?.asInt ?: 1
                amount * 2
            }
            "BATTLE" -> {
                val level = if (battleTargetLevel > 0) battleTargetLevel else 20
                (level * 10).coerceAtMost(300)
            }
            "TREASURE" -> {
                val dist = quest.get("requiredDistance")?.asDouble ?: 1000.0
                (dist / 10.0).toInt()
            }
            "ADVICE" -> 150
            else -> 50
        }
        
        player.giveExperiencePoints(xp)
        player.sendSystemMessage(
            Component.translatable("cobblebrain.quest.xp_reward", xp)
                .withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC)
        )
    }

    // guarda o pitch atual de cada Pokémon ativo
    private val pokemonPitchMap = mutableMapOf<UUID, Float>()

    private val bubbleProgress = mutableMapOf<UUID, Int>()          // standUuid -> chars revelados
    private val bubbleText = mutableMapOf<UUID, String>()           // standUuid -> texto completo
    private val bubbleSpeed = mutableMapOf<UUID, Int>()             // standUuid -> chars por tick

    val lastPositions = mutableMapOf<String, Vec3>()


    fun onPlayerJoin(player: ServerPlayer) {
        // Limpa estados de espera de missão ao entrar
        isWaitingForQuestResponse[player.uuid] = false
        pendingInterruption[player.uuid] = false

        player.sendSystemMessage(
            Component.translatable("cobblebrain.welcome.title")
                .withStyle(ChatFormatting.YELLOW)

                .append(Component.translatable("cobblebrain.welcome.line1").withStyle(ChatFormatting.YELLOW))
                .append(Component.translatable("cobblebrain.welcome.line1_cmd").withStyle(ChatFormatting.AQUA))
                .append(Component.translatable("cobblebrain.welcome.line1_end").withStyle(ChatFormatting.YELLOW))

                .append(Component.translatable("cobblebrain.welcome.line2").withStyle(ChatFormatting.YELLOW))
                .append(Component.translatable("cobblebrain.welcome.line2_cmd").withStyle(ChatFormatting.AQUA))
                .append(Component.translatable("cobblebrain.welcome.line2_end").withStyle(ChatFormatting.YELLOW))

                .append(Component.translatable("cobblebrain.welcome.line3").withStyle(ChatFormatting.YELLOW))
                .append(Component.translatable("cobblebrain.welcome.line3_key").withStyle(ChatFormatting.AQUA))
                .append(Component.translatable("cobblebrain.welcome.line3_end").withStyle(ChatFormatting.YELLOW))

                .append(Component.translatable("cobblebrain.welcome.line4").withStyle(ChatFormatting.YELLOW))
                .append(Component.translatable("cobblebrain.welcome.line4_option").withStyle(ChatFormatting.AQUA))
                .append(Component.translatable("cobblebrain.welcome.line4_mid").withStyle(ChatFormatting.YELLOW))
                .append(Component.translatable("cobblebrain.welcome.line4_item").withStyle(ChatFormatting.AQUA))
                .append(Component.translatable("cobblebrain.welcome.line4_end").withStyle(ChatFormatting.YELLOW))


            //.append(Component.literal("Activate 'Output April Fools Actions' in the config menu and use these new actions:\n").withStyle(ChatFormatting.LIGHT_PURPLE))
                //.append(Component.literal("(fire pokemon) fireball machine\n").withStyle(ChatFormatting.LIGHT_PURPLE))
                //.append(Component.literal("(fire pokemon) nuke\n").withStyle(ChatFormatting.LIGHT_PURPLE))
                //.append(Component.literal("(electric pokemon) final judgment\n").withStyle(ChatFormatting.LIGHT_PURPLE))
                //.append(Component.literal("(fairy pokemon) imaginary technique\n").withStyle(ChatFormatting.LIGHT_PURPLE))
                //.append(Component.literal("(psychic pokemon) psychic stand").withStyle(ChatFormatting.LIGHT_PURPLE))
                //.append(Component.literal("ssstyle\n").withStyle(ChatFormatting.LIGHT_PURPLE))

        )
    }

    fun onChat(sender: ServerPlayer, rawContent: String) {
        lastPlayerMessage[sender.uuid] = rawContent
        if (!config.listenToChat) return

        if (config.onlyNearbyChat) {

            val nearbyPlayers = sender.server.playerList.players.filter {
                it == sender || it.distanceTo(sender) <= 15.0
            }

            val hasOtherNearby = sender.server.playerList.players.any {
                it != sender && it.distanceTo(sender) <= 15.0
            }

            if (!hasOtherNearby) {
                sender.sendSystemMessage(
                    Component.translatable("cobblebrain.only_nearby_messages.warning")
                )
            }

            nearbyPlayers.forEach { player ->

                val conteudo = if (player == sender) {

                    """
            [CHAT MESSAGE]
            ${sender.name.string} (OWNER OF THIS TEAM) said to their Pokémon team:
            "$rawContent"
            """.trimIndent()

                } else {

                    """
            [CHAT MESSAGE]
            ${sender.name.string} (EXTERNAL PLAYER) said nearby:
            "$rawContent"

            IMPORTANT:
            - The Pokémon team belongs to ${player.name.string}
            - ${sender.name.string} is NOT the owner of this team
            - Treat ${sender.name.string} as another nearby person
            """.trimIndent()
                }

                onPlayerChat(player, conteudo)
            }

        } else {

            sender.server.playerList.players.forEach { player ->

                val conteudo = if (player == sender) {

                    """
            [CHAT MESSAGE]
            ${sender.name.string} (OWNER OF THIS TEAM) said to their Pokémon team:
            "$rawContent"
            """.trimIndent()

                } else {

                    """
            [CHAT MESSAGE]
            ${sender.name.string} (EXTERNAL PLAYER) said nearby:
            "$rawContent"

            IMPORTANT:
            - The Pokémon team belongs to ${player.name.string}
            - ${sender.name.string} is NOT the owner of this team
            - Treat ${sender.name.string} as another nearby person
            """.trimIndent()
                }

                onPlayerChat(player, conteudo)
            }
        }
    }

    fun onDamage(entity: LivingEntity, source: DamageSource, amount: Float, newHealth: Float) {
        when (entity) {
            is ServerPlayer -> {
                if (OfflinePlayers.isOffline(entity.uuid)) return
                val ativos = PokemonQuery.findActivePokemon(entity)
                if (ativos.isEmpty()) return

                val now = System.currentTimeMillis()
                val last = lastPrompt[entity.uuid] ?: 0L
                if (now - last >= 22000 && config.dialogueOnDamage) {
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
                if (ownerUuid != null && config.dialogueOnDamage) {
                    if (OfflinePlayers.isOffline(ownerUuid)) return
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
        StoryTimerSystem.tick(server)
        AmbientReactionManager.tick(server)

        maintainLookAt(server)

        if (server.tickCount % 200 == 0) {
            for (player in server.playerList.players) {

                runSocialTick(player)

                if (
                    OfflinePlayers.offlineMode[player.uuid] == true
                ) {
                    OfflineEventHandler.tick(player)
                }
            }
        }

        // Cleanup used barrels
        val it = pendingBarrelRemovals.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            val pos = entry.key
            val targetTick = entry.value
            
            if (server.tickCount >= targetTick) {
                val level = server.getLevel(pos.dimension()) ?: server.overworld()
                val blockPos = pos.pos()
                level.setBlock(blockPos, Blocks.AIR.defaultBlockState(), 3)
                // Partículas de fumaça para o sumiço
                level.sendParticles(
                    ParticleTypes.LARGE_SMOKE,
                    blockPos.x + 0.5, blockPos.y + 0.5, blockPos.z + 0.5,
                    10, 0.2, 0.2, 0.2, 0.05
                )
                it.remove()
            }
        }

        for (player in server.playerList.players) {
            val activeQuests = CobblebrainWorldSave.getActiveQuests(player)
            for (quest in activeQuests) {
                handleQuestTick(player, quest)
            }
        }

        if (server.tickCount % 40 == 0) {
            validateItemQuests(server)
            for (player in server.playerList.players) {
                syncQuests?.invoke(player)
            }
        }

        // Cleanup stale quest response states (30s timeout)
        val currentTick = server.tickCount.toLong()
        val playersWaiting = isWaitingForQuestResponse.keys.toList()
        for (playerUuid in playersWaiting) {
            if (isWaitingForQuestResponse[playerUuid] == true) {
                val timeout = questResponseTimeout[playerUuid] ?: 0L
                if (currentTick >= timeout) {
                    isWaitingForQuestResponse[playerUuid] = false
                    pendingInterruption[playerUuid] = false
                }
            }
        }
    }

    // bridge de networking (Fabric vai implementar)
    var sendToPlayer: ((ServerPlayer, String) -> Unit)? = null
    var sendPersonalityList: ((ServerPlayer, String) -> Unit)? = null
    var syncQuests: ((ServerPlayer) -> Unit)? = null
    var onSendPromptClient: (() -> Unit)? = null

    fun onBattleStarted(event: BattleStartedEvent) {
        val battle = event.battle
        val server = battle.players.firstOrNull()?.server ?: return

        if (!config.dialogueOnBattle) return

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

            if (OfflinePlayers.isOffline(player.uuid)) return@execute

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
                "IMPORTANT: ${player.name.string} started a battle with my team[$meusNomes] against [$inimigosNomes]"
            )

            sendToPlayer?.invoke(player, prompt)
        }
    }

    fun onPokemonSent(event: PokemonSentEvent) {
        val pokemon = event.pokemon
        val ownerId = pokemon.getOwnerUUID() ?: return
        val ownerPlayer = pokemon.getOwnerPlayer() ?: return

        // Sync cooldowns for player
        vito.cobblebrain.sensors.PokemonCommands.syncCooldowns(ownerPlayer)
        
        val battle = BattleRegistry.getBattleByParticipatingPlayerId(ownerId)
        if (battle != null && config.dialogueOnBattle) {
            val ownerPlayer = pokemon.getOwnerPlayer() ?: return

            if (OfflinePlayers.isOffline(ownerPlayer.uuid)) return

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

        if (!config.dialogueOnBattle) return
        if (uuids.isEmpty()) return

        val server = currentServer ?: return

        uuids.forEach { uuid ->
            val player = server.playerList.getPlayer(uuid) ?: return@forEach

            if (OfflinePlayers.isOffline(player.uuid)) return@forEach

            scheduledMessages[player.uuid]?.clear()

            val ativos = PokemonQuery.findActivePokemon(player)

            val prompt = buildPrompt(
                player,
                ativos,
                "IMPORTANT: ${player.name.string} run away from the battle"
            )

            sendToPlayer?.invoke(player, prompt)
        }
    }

    fun onBattleVictory(event: BattleVictoryEvent) {
        val battle = event.battle
        if (!config.dialogueOnBattle) return

        for (player in battle.players) {
            val myActor = battle.actors.firstOrNull { it.uuid == player.uuid } ?: continue

            scheduledMessages[player.uuid]?.clear()

            if (event.winners.contains(myActor)) {
                var sent = false

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

                            val storyId = activeQuest.get("storyId")?.asString ?: "generic"

                            if (storyId != "generic") {
                                player.sendSystemMessage(
                                    Component.translatable("cobblebrain.quest.story_event", targetSpecies, giverName)
                                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                                )
                            } else {
                                player.sendSystemMessage(
                                    Component.translatable("cobblebrain.quest.battle_success", targetSpecies, giverName)
                                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                                )
                                CobblebrainWorldSave.moveQuest(
                                    player.uuid.toString(),
                                    activeQuest.get("giverUuid").asString,
                                    "BATTLE",
                                    "COMPLETED"
                                )
                            }
                            

                            if (!OfflinePlayers.isOffline(player.uuid)) {
                                val prompt = buildPrompt(
                                    player,
                                    ativos,
                                    "IMPORTANT: ${player.name.string} defeated the $targetSpecies! $giverName thanks him and reacts to the victory."
                                )

                                isWaitingForQuestResponse[player.uuid] = true
                                questResponseTimeout[player.uuid] = player.server.tickCount.toLong() + 600 // 30s
                                sendToPlayer?.invoke(player, prompt)
                            }

                            adjustKarma(player, giverName, 2)
                            giveReward(player, giverName)
                            val targetLevel = loser.pokemonList.firstOrNull()?.originalPokemon?.level ?: 0
                            giveQuestXp(player, activeQuest, targetLevel)

                            sent = true
                        }
                    }
                }

                // Vitória sem quest
                if (!sent) {
                    if (!OfflinePlayers.isOffline(player.uuid)) {
                        val ativos = PokemonQuery.findActivePokemon(player)

                        val prompt = buildPrompt(
                            player,
                            ativos,
                            "IMPORTANT: ${player.name.string} team has just won a battle. The Pokémon react to the victory based on their personalities."
                        )

                        sendToPlayer?.invoke(player, prompt)
                    }
                }

            } else {
                // Derrota
                // Sync cooldowns for player
                vito.cobblebrain.sensors.PokemonCommands.syncCooldowns(player)
                if (!OfflinePlayers.isOffline(player.uuid)) {
                    val ativos = PokemonQuery.findActivePokemon(player)

                    val prompt = buildPrompt(
                        player,
                        ativos,
                        "IMPORTANT: ${player.name.string} team has lost the battle. Pokémon are exhausted or knocked out, and should only react if they are still able to act and have more than 0 HP. Any response should reflect defeat, fatigue, or frustration."
                    )

                    sendToPlayer?.invoke(player, prompt)
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

    fun onPlayerChat(player: ServerPlayer, text: String): Boolean {
        if (OfflinePlayers.isOffline(player.uuid)) {
            if (OfflinePlayers.isOfflineTalk(player.uuid)) {
                OfflineDialogueManager.handleOfflineTalk(player)
            }
            return true
        }

        if (isWaitingForQuestResponse[player.uuid] == true) {
            if (pendingInterruption[player.uuid] != true) {
                player.sendSystemMessage(
                    Component.translatable("cobblebrain.quest.interrupt_warning")
                        .withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC)
                )
                pendingInterruption[player.uuid] = true
                return false
            }
        }

        // Reset states if message goes through
        isWaitingForQuestResponse[player.uuid] = false
        pendingInterruption[player.uuid] = false

        scheduledMessages[player.uuid]?.clear()
        justSentMessage[player.uuid] = true
        val ativos = PokemonQuery.findActivePokemon(player)
        val prompt = buildPrompt(player, ativos, "\n\n$text")

        // envia prompt para o cliente processar
        sendToPlayer?.invoke(player, prompt)
        return true
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
                        (!config.showFriendship && (msg.text.startsWith("friendship", ignoreCase = true) || (msg.text.startsWith("%") && msg.text.contains(":"))))
                    ) {
                        return@forEach
                    }
                    println("=== DEBUG FLUSH ===")
                    println("msg.text='${msg.text}'")
                    println("msg.speaker='${msg.speaker}'")

                    if (config.dialogueInChat) {
                        val text = msg.text

                        // Regex para detectar "!Error 123!"
                        val regex = Regex("!Error \\d{3}!")

                        val component = if (
                            config.showFriendship &&
                            text.startsWith("%") &&
                            text.contains(":")
                        ) {
                            val name = text.substring(1).substringBefore(":").trim()
                            val change = text.substringAfter(":").trim()
                            Component.literal("Friendship $name: $change").withStyle(ChatFormatting.GREEN)
                        } else if (regex.containsMatchIn(text)) {
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

                    val wildEntities = collectWorldContext(msg.player).nearbyPokemonEntities
                    val wilds = wildEntities.map { it.pokemon }

                    val participantes = ativos + wilds

                    participantes.forEach { poke ->
                        println("participante nickname='${poke.nickname}' especie='${poke.species.resourceIdentifier.path}'")
                    }

                    val rawName = msg.text.substringBefore(":").trim()

                    val speaker = msg.speaker ?: run {

                        val aliases =
                            pokemonAliasMap[msg.player.uuid]
                                ?: emptyMap()

                        // MATCH EXATO
                        var uuid = aliases[rawName]

                        // MATCH IGNORANDO CASE
                        if (uuid == null) {
                            uuid = aliases.entries.firstOrNull {
                                it.key.equals(rawName, ignoreCase = true)
                            }?.value
                        }

                        // FALLBACK:
                        // tenta remover #1/#2
                        if (uuid == null) {
                            val cleaned =
                                rawName.substringBefore("#").trim()
                            uuid = aliases.entries.firstOrNull {
                                it.key.substringBefore("#")
                                    .equals(cleaned, ignoreCase = true)
                            }?.value
                        }
                        participantes.find { it.uuid == uuid }
                    }

                    println("RAW NAME = $rawName")
                    println("ALIASES = ${pokemonAliasMap[msg.player.uuid]}")
                    println("SPEAKER = ${speaker?.species?.name}")

                    println("speaker resolvido = ${speaker?.nickname ?: speaker?.species?.resourceIdentifier?.path ?: "null"}")

                    speaker?.let { pokemon ->
                        val entity = pokemon.entity
                        val basePitch = entity?.uuid?.let { pokemonPitchMap[it] } ?: 1.0f
                        expressPokemon(pokemon, basePitch + msg.pitchMod)

                        if (entity != null && config.chatbubbles) {
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

    fun giveReward(player: ServerPlayer, giverName: String, isAdvice: Boolean = false) {
        val karmaRoot = CobblebrainWorldSave.data.getAsJsonObject("karma") ?: JsonObject().also { CobblebrainWorldSave.data.add("karma", it) }
        val playerKey = player.uuid.toString()

        val effectiveKarma = if (config.enableKarma) {
            var karma = if (karmaRoot.has(playerKey)) {
                karmaRoot.getAsJsonObject(playerKey).get(giverName)?.asInt ?: 0
            } else 0

            if (isAdvice) {
                karma -= 4
            }

            karma
        } else {
            0
        }

        val random = player.server.overworld().random
        val roll = random.nextFloat() * 100f

        val selectedTier = when {
            effectiveKarma >= 12 -> when {
                roll < 5 -> 3
                roll < 20 -> 2
                roll < 50 -> 1
                else -> 0
            }
            effectiveKarma >= 7 -> when {
                roll < 10 -> 2
                roll < 40 -> 1
                else -> 0
            }
            effectiveKarma >= 3 -> when {
                roll < 30 -> 1
                else -> 0
            }
            else -> 0
        }

        val rewardItem = when (selectedTier) {
            3 -> { // EPIC
                val subRoll = random.nextFloat() * 100f
                if (subRoll < 30) { // 30% de 5% = 1.5% de chance total
                    ItemStack(CobblemonItems.MASTER_BALL, 1)
                } else {
                    listOf(
                        ItemStack(CobblemonItems.EXPERIENCE_CANDY_L, 1),
                        ItemStack(CobblemonItems.RARE_CANDY, Random.nextInt(1, 3)),
                        ItemStack(Items.GOLD_INGOT, Random.nextInt(3, 7))
                    ).random()
                }
            }
            2 -> listOf( // RARE
                ItemStack(CobblemonItems.ULTRA_BALL, Random.nextInt(2, 6)),
                ItemStack(CobblemonItems.EXPERIENCE_CANDY_M, Random.nextInt(1, 4)),
                ItemStack(CobblemonItems.RELIC_COIN_SACK, 1),
                ItemStack(Items.GOLDEN_APPLE, 1)
            ).random()
            1 -> listOf( // UNCOMMON
                ItemStack(CobblemonItems.GREAT_BALL, Random.nextInt(3, 7)),
                ItemStack(CobblemonItems.EXPERIENCE_CANDY_S, Random.nextInt(2, 6)),
                ItemStack(CobblemonItems.REVIVE, Random.nextInt(1, 3)),
                ItemStack(Items.IRON_INGOT, Random.nextInt(2, 5))
            ).random()
            else -> listOf( // COMMON
                ItemStack(Items.SWEET_BERRIES, Random.nextInt(8, 17)),
                ItemStack(CobblemonItems.POKE_BALL, Random.nextInt(4, 9)),
                ItemStack(Items.APPLE, Random.nextInt(3, 8)),
                ItemStack(Items.WHEAT, Random.nextInt(6, 13))
            ).random()
        }

        val count = rewardItem.count
        val itemNameComponent = rewardItem.hoverName.copy()
        val isMasterBall = rewardItem.item == CobblemonItems.MASTER_BALL

        // Som épico se for Master Ball
        if (isMasterBall) {
            player.playNotifySound(
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                SoundSource.MASTER,
                1.0f,
                1.0f
            )
        }

        if (!player.inventory.add(rewardItem)) {
            player.drop(rewardItem, false)
        }

        val messageColor = if (isMasterBall) ChatFormatting.GOLD else ChatFormatting.AQUA

        player.sendSystemMessage(
            Component.literal("")
                .append(Component.literal(giverName).withStyle(messageColor))
                .append(Component.literal(" gave you a gift: ").withStyle(messageColor))
                .append(Component.literal("x$count ").withStyle(messageColor))
                .append(itemNameComponent.withStyle(messageColor).withStyle { it.withBold(isMasterBall) })
                .append(Component.literal("!").withStyle(messageColor))
        )
    }

    fun validateQuestGiversOnPlayerJoin(server: MinecraftServer, player: ServerPlayer) {
        val data = CobblebrainWorldSave.data
        if (!data.has("quests")) return

        val questsRoot = data.getAsJsonObject("quests")

        val abandonedArray = questsRoot.getAsJsonArray("abandoned") ?: JsonArray().also {
            questsRoot.add("abandoned", it)
        }

        val playerChunk = player.chunkPosition()
        val radius = 3
        val playerLevel = player.serverLevel()

        // Validate secondary quests
        val secondaryArray = questsRoot.getAsJsonArray("active_secondary")
        if (secondaryArray != null) {
            val iterator = secondaryArray.iterator()
            while (iterator.hasNext()) {
                val questObj = iterator.next().asJsonObject
                if (!questObj.has("status") || !questObj.has("ownerUuid") || !questObj.has("giverUuid")) continue
                if (questObj.get("status").asString != "IN_PROGRESS") continue
                if (questObj.get("ownerUuid").asString != player.uuid.toString()) continue

                val giverUuid = try { UUID.fromString(questObj.get("giverUuid").asString) } catch (e: Exception) { continue }
                val giverEntity = server.allLevels.firstNotNullOfOrNull { it.getEntity(giverUuid) }

                if (giverEntity is PokemonEntity && giverEntity.level() == playerLevel) {
                    val giverChunk = giverEntity.chunkPosition()
                    val dx = giverChunk.x - playerChunk.x
                    val dz = giverChunk.z - playerChunk.z
                    if (kotlin.math.abs(dx) <= radius && kotlin.math.abs(dz) <= radius && playerLevel.hasChunk(giverChunk.x, giverChunk.z)) {
                        val giverName = giverEntity.pokemon.nickname?.string ?: giverEntity.pokemon.species.resourceIdentifier.path
                        player.sendSystemMessage(Component.translatable("cobblebrain.quest.still_active", giverName).withStyle(ChatFormatting.GREEN))
                        continue
                    }
                }

                iterator.remove()
                questObj.addProperty("status", "ENF")
                abandonedArray.add(questObj)
                player.sendSystemMessage(Component.translatable("cobblebrain.quest.abandoned.entity_not_found").withStyle(ChatFormatting.YELLOW))
            }
        }

        // Validate story quest
        val storyObj = questsRoot.getAsJsonObject("active_story")
        if (storyObj != null && storyObj.has("status") && storyObj.get("status").asString == "IN_PROGRESS" &&
            storyObj.has("ownerUuid") && storyObj.get("ownerUuid").asString == player.uuid.toString()) {
            val giverUuid = try { UUID.fromString(storyObj.get("giverUuid").asString) } catch (e: Exception) { null }
            val giverEntity = giverUuid?.let { id -> server.allLevels.firstNotNullOfOrNull { it.getEntity(id) } }
            if (giverEntity !is PokemonEntity) {
                storyObj.addProperty("status", "ENF")
                abandonedArray.add(storyObj)
                questsRoot.add("active_story", JsonObject())
                player.sendSystemMessage(Component.translatable("cobblebrain.quest.story_abandoned").withStyle(ChatFormatting.YELLOW))
            }
        }

        CobblebrainWorldSave.save()
    }

    fun validateItemQuests(server: MinecraftServer) {
        val questsObj = CobblebrainWorldSave.data.getAsJsonObject("quests") ?: return
        val storyObj = questsObj.getAsJsonObject("active_story") ?: JsonObject()
        val secondaryArray = questsObj.getAsJsonArray("active_secondary") ?: JsonArray()

        // Coleta quests de ambos os slots
        val allActive = mutableListOf<JsonObject>()
        if (storyObj.has("type") && storyObj.get("type").asString == "ITEM") allActive.add(storyObj)
        secondaryArray.map { it.asJsonObject }.filterTo(allActive) { it.get("type").asString == "ITEM" }

        val quests = allActive.filter { it.get("status").asString == "IN_PROGRESS" }

        quests.forEach { questObj ->

            val giverUuid = UUID.fromString(questObj.get("giverUuid").asString)
            val ownerUuid = UUID.fromString(questObj.get("ownerUuid").asString)

            val target = questObj.get("target").asString
            val amount = questObj.get("amount").asInt

            val giverEntity = server.allLevels.firstNotNullOfOrNull { it.getEntity(giverUuid) } as? PokemonEntity ?: return@forEach
            val level = giverEntity.level() as ServerLevel
            val player = server.playerList.getPlayer(ownerUuid) ?: return@forEach

            val nearbyItems = level.getEntitiesOfClass(
                ItemEntity::class.java,
                giverEntity.boundingBox.inflate(5.0)
            )

            val collected = nearbyItems
                .filter { BuiltInRegistries.ITEM.getKey(it.item.item).path == target }
                .sumOf { it.item.count }
            
            questObj.addProperty("collected", collected)

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

                player.sendSystemMessage(
                    Component.translatable("cobblebrain.quest.item_success", target, giverName)
                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                )
                
                adjustKarma(player, giverName, 2)
                giveReward(player, giverName)
                giveQuestXp(player, questObj)

                if (!OfflinePlayers.isOffline(player.uuid)) {
                    val prompt = buildPrompt(
                        player,
                        PokemonQuery.findActivePokemon(player),
                        "IMPORTANT: Mission concluded! $giverName thanks the player for bringing the $amount $target(s)!"
                    )
                    isWaitingForQuestResponse[player.uuid] = true
                    questResponseTimeout[player.uuid] = level.server.tickCount.toLong() + 600
                    sendToPlayer?.invoke(player, prompt)
                }
            }
        }
    }

    fun handleQuestTick(player: ServerPlayer, quest: JsonObject) {
        val type = quest.get("type").asString
        if (type == "BATTLE") {
            handleBattleQuestTick(player, quest)
        } else if (type == "TREASURE") {
            handleTreasureQuestTick(player, quest)
        }
    }

    fun handleBattleQuestTick(player: ServerPlayer, quest: JsonObject) {
        // Now handled at quest creation
    }

    fun handleTreasureQuestTick(player: ServerPlayer, quest: JsonObject) {
        val tx = quest.get("targetX").asInt
        val ty = quest.get("targetY").asInt
        val tz = quest.get("targetZ").asInt
        
        val pos = BlockPos(tx, ty, tz)
        val distance = player.blockPosition().distManhattan(pos)

        // 1. Efeito visual: Círculo de partículas na superfície quando perto
        if (distance < 30) {
            val level = player.serverLevel()
            // Desenha um círculo de partículas a cada 2 ticks para poupar performance
            if (player.tickCount % 2 == 0) {
                val radius = 4.0
                for (i in 0 until 8) {
                    val angle = i * Math.PI * 2 / 8
                    val px = tx + 0.5 + kotlin.math.cos(angle) * radius
                    val pz = tz + 0.5 + kotlin.math.sin(angle) * radius
                    // Pega a altura do chão naquele ponto das partículas
                    val py = level.getHeight(Heightmap.Types.MOTION_BLOCKING, px.toInt(), pz.toInt()).toDouble()
                    
                    level.sendParticles(
                        ParticleTypes.HAPPY_VILLAGER,
                        px, py + 0.2, pz,
                        1, 0.0, 0.1, 0.0, 0.02
                    )
                }
            }
            
            val scanned = quest.get("scanned")?.asBoolean ?: false
            if (!scanned && distance < 15) {
                quest.addProperty("scanned", true)
                CobblebrainWorldSave.save()
                player.sendSystemMessage(Component.translatable("cobblebrain.quest.barrel_found").withStyle(ChatFormatting.AQUA))
            }
        }

        // 2. Verifica conclusão (se o jogador ABRIR o barril)
        if (distance <= 4.5) {
            val level = player.serverLevel()
            val state = level.getBlockState(pos)
            
            // Verifica se o bloco é um barril e se está no estado OPEN
            val isOpen = state.block == Blocks.BARREL && state.getValue(net.minecraft.world.level.block.BarrelBlock.OPEN)

            if (isOpen) {
                val giverUuid = quest.get("giverUuid").asString
                val giverName = CobblebrainWorldSave.getGiverNameFromQuest(quest)

                // Get items from barrel
                val itemsList = mutableListOf<String>()
                val barrelEntity = level.getBlockEntity(pos) as? net.minecraft.world.level.block.entity.BarrelBlockEntity
                if (barrelEntity != null) {
                    for (i in 0 until barrelEntity.containerSize) {
                        val stack = barrelEntity.getItem(i)
                        if (!stack.isEmpty) {
                            val itemName = stack.item.descriptionId.split(".").last().replace("_", " ")
                            itemsList.add("${stack.count}x $itemName")
                        }
                    }
                }
                val itemsStr = if (itemsList.isEmpty()) "nothing (it was empty!)" else itemsList.joinToString(", ")
                
                if (!OfflinePlayers.isOffline(player.uuid)) {
                    val ativos = PokemonQuery.findActivePokemon(player)
                    val prompt = buildPrompt(player, ativos, "IMPORTANT: ${player.name.string} has successfully found the item storage! Inside, they found: $itemsStr. Talk about the items found and thank the player!")
                    isWaitingForQuestResponse[player.uuid] = true
                    questResponseTimeout[player.uuid] = level.server.tickCount.toLong() + 600
                    sendToPlayer?.invoke(player, prompt)
                }

                player.sendSystemMessage(
                    Component.translatable("cobblebrain.quest.treasure_success", giverName)
                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                )

                CobblebrainWorldSave.moveQuest(player.uuid.toString(), giverUuid, "TREASURE", "COMPLETED")
                adjustKarma(player, giverName, 3) // Ganha 3 de karma por achar tesouro
                giveQuestXp(player, quest)
                
                // Marca para sumir daqui a 1 tick (praticamente instantâneo)
                pendingBarrelRemovals[net.minecraft.core.GlobalPos.of(level.dimension(), pos)] = level.server.tickCount.toLong() + 1
            }
        }
    }

    fun onBlockBreak(player: ServerPlayer, pos: BlockPos, state: net.minecraft.world.level.block.state.BlockState) {
        // Se for um barril sendo destruído
        if (state.block == Blocks.BARREL) {
            val allSecondary = CobblebrainWorldSave.getSecondaryQuestsForAll()

            // Filtra as missões que estavam naquele bloco
            val affectedQuests = mutableListOf<JsonObject>()
            for (i in 0 until allSecondary.size()) {
                val q = allSecondary.get(i).asJsonObject
                if (q.get("type").asString == "TREASURE" && q.get("status").asString == "IN_PROGRESS") {
                    val tx = q.get("targetX").asInt
                    val ty = q.get("targetY").asInt
                    val tz = q.get("targetZ").asInt
                    if (pos.x == tx && pos.y == ty && pos.z == tz) {
                        affectedQuests.add(q)
                    }
                }
            }

            affectedQuests.forEach { q ->
                val ownerUuid = q.get("ownerUuid").asString
                val giverUuid = q.get("giverUuid").asString
                val giverName = CobblebrainWorldSave.getGiverNameFromQuest(q)

                if (player.uuid.toString() == ownerUuid) {
                    // O dono quebrou o próprio tesouro!
                    adjustKarma(player, giverName, -3)
                    
                    if (!OfflinePlayers.isOffline(player.uuid)) {
                        val ativos = PokemonQuery.findActivePokemon(player)
                        val prompt = buildPrompt(player, ativos, "IMPORTANT: ${player.name.string} has DESTROYED the item storage that was part of the quest! The pokemon is very upset! React to this destruction!")
                        isWaitingForQuestResponse[player.uuid] = true
                        questResponseTimeout[player.uuid] = player.server?.tickCount?.toLong()?.plus(600) ?: 0L
                        sendToPlayer?.invoke(player, prompt)
                    }
                    
                    CobblebrainWorldSave.failQuest(ownerUuid, giverUuid, "TREASURE")

                    player.sendSystemMessage(
                        Component.translatable("cobblebrain.quest.treasure_failed.self")
                            .withStyle(ChatFormatting.RED)
                    )
                } else {
                    // Outro player quebrou
                    val owner = player.server.playerList.getPlayer(UUID.fromString(ownerUuid))
                    if (owner != null) {
                        if (!OfflinePlayers.isOffline(owner.uuid)) {
                            val ativos = PokemonQuery.findActivePokemon(owner)
                            val prompt = buildPrompt(owner, ativos, "IMPORTANT: Someone else (not ${player.name.string}) has destroyed or stolen the item storage from the quest! React with shock and tell the player about it!")
                            isWaitingForQuestResponse[owner.uuid] = true
                            questResponseTimeout[owner.uuid] = owner.server?.tickCount?.toLong()?.plus(600) ?: 0L
                            sendToPlayer?.invoke(owner, prompt)
                        }
                        
                        CobblebrainWorldSave.failQuest(ownerUuid, giverUuid, "TREASURE")

                        owner.sendSystemMessage(
                            Component.translatable("cobblebrain.quest.treasure_failed.other")
                                .withStyle(ChatFormatting.RED)
                        )
                    } else {
                        // Se o dono estiver offline, apenas falha a missão no arquivo
                        CobblebrainWorldSave.failQuest(ownerUuid, giverUuid, "TREASURE")
                    }
                }
            }
        }
    }

    fun handleAdviceQuestResponse(player: ServerPlayer, giver: PokemonEntity?, response: String) {
        if (OfflinePlayers.isOffline(player.uuid)) return
        val quests = CobblebrainWorldSave.getActiveQuests(player)
        val quest = quests.firstOrNull { it.get("type").asString == "ADVICE" } ?: return

        val giverName = giver?.pokemon?.nickname?.string
            ?: giver?.pokemon?.species?.resourceIdentifier?.path
            ?: CobblebrainWorldSave.getGiverNameFromQuest(quest)

        val scoreRegex = Regex("""#SCORE:\s*([+-]?\d+)""")
        val match = scoreRegex.find(response)
        
        if (match != null) {
            val change = match.groupValues[1].toInt()
            println("[DEBUG] ADVICE Score match found: $change")
            
            val currentPoints = quest.get("points")?.asInt ?: 0
            val newPoints = currentPoints + change
            quest.addProperty("points", newPoints)
            
            player.sendSystemMessage(
                Component.translatable("cobblebrain.quest.advice_update", giverName)
                    .append(Component.translatable("cobblebrain.quest.advice_points", "${if (change >= 0) "+" else ""}$change").withStyle(if (change >= 0) ChatFormatting.GREEN else ChatFormatting.RED))
                    .append(Component.translatable("cobblebrain.quest.advice_total", newPoints))
                    .withStyle(ChatFormatting.YELLOW)
            )
            
            
            // Completion Check
            if (newPoints >= 5) {
                player.sendSystemMessage(
                    Component.translatable("cobblebrain.quest.advice_success", giverName)
                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                )
                CobblebrainWorldSave.moveQuest(
                    player.uuid.toString(),
                    quest.get("giverUuid").asString,
                    "ADVICE",
                    "COMPLETED"
                )
                adjustKarma(player, giverName, 5)
                giveReward(player, giverName, isAdvice = true)
                giveQuestXp(player, quest)
                val ativos = PokemonQuery.findActivePokemon(player)
                val prompt = buildPrompt(
                    player,
                    ativos,
                    "IMPORTANT: Advice Quest Success! $giverName is very happy with the player's advice! Conclude the topic and thank the player!"
                )
                isWaitingForQuestResponse[player.uuid] = true
                questResponseTimeout[player.uuid] = player.server?.tickCount?.toLong()?.plus(600) ?: 0L
                sendToPlayer?.invoke(player, prompt)
            } else if (newPoints <= -5) {
                player.sendSystemMessage(
                    Component.translatable("cobblebrain.quest.advice_failed", giverName)
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                )
                CobblebrainWorldSave.moveQuest(
                    player.uuid.toString(),
                    quest.get("giverUuid").asString,
                    "ADVICE",
                    "ABANDONED"
                )
                adjustKarma(player, giverName, -5)
                val ativos = PokemonQuery.findActivePokemon(player)
                val prompt = buildPrompt(
                    player,
                    ativos,
                    "IMPORTANT: Advice Quest Failed! $giverName was offended or ignored the advice! React to this failure with frustration or disappointment!"
                )
                isWaitingForQuestResponse[player.uuid] = true
                questResponseTimeout[player.uuid] = player.server?.tickCount?.toLong()?.plus(600) ?: 0L
                sendToPlayer?.invoke(player, prompt)
            }
            
            CobblebrainWorldSave.save()
        }
    }

    fun abandonQuest(player: ServerPlayer) {
        val secondaryQuests = CobblebrainWorldSave.getSecondaryQuests(player)
        if (secondaryQuests.isEmpty()) {
            player.sendSystemMessage(
                Component.translatable("cobblebrain.quest.no_active_to_abandon")
                    .withStyle(ChatFormatting.RED)
            )
            return
        }

        val quest = secondaryQuests.last()
        val giverUuid = quest.get("giverUuid").asString
        val type = quest.get("type").asString
        val giverName = CobblebrainWorldSave.getGiverNameFromQuest(quest)

        // 1. Remove follower if any
        val iterator = CobblebrainWorldSave.followers.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val triple = entry.value
            val pokemon = triple.first
            val questOwner = triple.second
            val goal = triple.third

            if (questOwner.uuid == player.uuid && pokemon.uuid.toString() == giverUuid) {
                MobBridge.removeGoal?.invoke(pokemon, goal)
                pokemon.navigation.stop()
                iterator.remove()
                println("[DEBUG] Follower removed for abandoned quest: $giverName")
            }
        }

        // 2. Move to abandoned in world save
        CobblebrainWorldSave.moveQuest(player.uuid.toString(), giverUuid, type, "ABANDONED")
        
        // 3. Set Pending Note for next prompt
        pendingQuestNote[player.uuid] = "The player recently ABANDONED a quest from $giverName."

        player.sendSystemMessage(
            Component.translatable("cobblebrain.quest.abandoned", giverName)
                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)
        )

        // 4. Trigger AI Reaction
        if (!OfflinePlayers.isOffline(player.uuid)) {
            val ativos = PokemonQuery.findActivePokemon(player)
            val prompt = buildPrompt(
                player,
                ativos,
                "IMPORTANT: ${player.name.string} has just ABANDONED the quest given by $giverName!"
            )
            isWaitingForQuestResponse[player.uuid] = true
            questResponseTimeout[player.uuid] = player.server?.tickCount?.toLong()?.plus(600) ?: 0L
            sendToPlayer?.invoke(player, prompt)
        }
        
        syncQuests?.invoke(player)
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
        if (OfflinePlayers.isOffline(player.uuid)) return
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

        val now = player.server?.tickCount?.toLong() ?: 0L

        val chance = config.spontaneousDialogueChance

        // DELETAR DPS DE TESTE
        println("Chance: $chance | Roll: ${Random.nextDouble()}")

        if (Random.nextDouble() <= chance) {

            player.sendSystemMessage(
                Component.translatable("cobblebrain.team.thinking")
                    .withStyle(ChatFormatting.YELLOW)
            )

            val topics = listOf(
                "their current feelings or mood",
                "something that happened recently",
                "a question or curiosity about the world",
                "something happening around them right now",
                "a fact or knowledge they know",

                "their opinion about the player",
                "something they want from the player",
                "their opinion about a teammate",
                "a recent interaction with another Pokémon",

                "their current physical state or needs",
                "a random thought or idea they had",
                "something confusing they are trying to understand",

                "a curiosity about the world or humans",
                "something they don't understand",

                "their instincts or natural urges",
                "something related to their type",

                "something they remember briefly about their past",

                "something they want to do next",
                "a goal or desire they have",

                "a joke or playful comment",
                "teasing the player or a teammate",
            )

            val currentTopic = PlayerTopicState.get(player.uuid)

            val last = PlayerConversationState.get(player.uuid) ?: now
            val delta = now - last

            val timeInstruction = when {
                delta < 200 ->
                    "This is a recent conversation. Continue the current topic naturally."
                delta < 600 ->
                    "Some time has passed. You may continue, but avoid repeating ideas from the LAST INTERACTIONS."
                delta < 1200 ->
                    "A while has passed. Avoid repeating ideas from the LAST INTERACTIONS and evolve the conversation into something new."
                else ->
                    "A long time has passed. The previous topic is no longer relevant. Start a new conversation and do not reuse ideas from the LAST INTERACTIONS."
            }

            // Atualiza tempo
            PlayerConversationState.update(player.uuid, now)

            val shouldChange = currentTopic == null || Random.nextDouble() < 0.3

            val selectedTopic = if (shouldChange) {
                val newTopic = topics.random()
                PlayerTopicState.set(player.uuid, newTopic)
                newTopic
            } else {
                currentTopic
            }

            val environmentTopics = setOf(
                "something happening around them right now",
                "a curiosity about the world or humans",
                "a question or curiosity about the world"
            )

            val needsEnvironment = selectedTopic in environmentTopics
            val useFullContext = needsEnvironment || Random.nextDouble() < 0.3

            val contextInstruction = if (useFullContext) {
                "The environment is relevant and can be used in the conversation."
            } else {
                "Focus less on the environment. Prioritize emotions, thoughts, interactions, or personal ideas instead of describing surroundings."
            }

            val prompt = buildPrompt(
                player,
                ativos,
                "IMPORTANT: The Pokémon are having a conversation about $selectedTopic. " +
                        "They should connect ideas naturally and develop the topic instead of changing it abruptly. " +
                        "$contextInstruction " +
                        "$timeInstruction " +
                        "Use the LAST INTERACTIONS as context to avoid repetition and to evolve the conversation naturally."
            )

            sendToPlayer?.invoke(player, prompt)

            println("[DEBUG] Spontaneous dialogue for ${player.name.string} | Topic: $selectedTopic | FullContext: $useFullContext")
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

        // a variação aleatória agora segue a direção do sentimento
        val randomOffset = when {
            basePitch > 1.0f -> Random.nextFloat() * 0.15f        // sempre mais agudo se feliz
            basePitch < 1.0f -> -(Random.nextFloat() * 0.15f)     // sempre mais grave se triste
            else -> Random.nextFloat() * 0.10f - 0.05f            // centralizado se neutro
        }

        val variedPitch = (basePitch + randomOffset).coerceIn(0.6f, 1.4f)

        // toca o cry com pitch variado
        playPokemonCry(pokemon, variedPitch)

        // só pula se estiver no chão
        if (entity != null && entity.onGround()) {
            entity.jumpFromGround()
        }

        // partículas apenas se houver mudança emocional (pitch != 1.0)
        val particleType = when {
            basePitch > 1.0f -> ParticleTypes.HEART
            basePitch < 1.0f -> ParticleTypes.ANGRY_VILLAGER
            else -> null
        }

        if (particleType != null) {
            level.sendParticles(
                particleType,
                entity.x, entity.y + entity.bbHeight / 2.0, entity.z,
                6,
                0.25, 0.35, 0.25,
                0.0
            )
        }
    }

    private fun generatePokemonAliases(
        player: ServerPlayer,
        pokemons: List<Pokemon>
    ) {
        val aliasMap = mutableMapOf<String, UUID>()
        val reverseMap = mutableMapOf<UUID, String>()

        val grouped = pokemons.groupBy {
            it.nickname?.string?.trim()
                ?.takeIf { name -> name.isNotBlank() }
                ?: it.species.name
        }

        grouped.forEach { (baseName, list) ->

            if (list.size == 1) {
                val pokemon = list.first()

                aliasMap[baseName] = pokemon.uuid
                reverseMap[pokemon.uuid] = baseName
            } else {

                list.forEachIndexed { index, pokemon ->

                    val alias = "$baseName#${index + 1}"

                    aliasMap[alias] = pokemon.uuid
                    reverseMap[pokemon.uuid] = alias
                }
            }
        }

        pokemonAliasMap[player.uuid] = aliasMap
        reversePokemonAliasMap[player.uuid] = reverseMap
    }

    fun buildPrompt(player: ServerPlayer, pokemons: List<Pokemon>, moreText: String): String {
        generatePokemonAliases(player, pokemons)
        val context = collectWorldContext(player)

        // RESET MAPS
        pokemonAliasMap[player.uuid] = mutableMapOf()
        reversePokemonAliasMap[player.uuid] = mutableMapOf()

        // TODOS OS POKÉMONS PARTICIPANTES
        val allPokemon = pokemons + context.nearbyPokemonEntities.map { it.pokemon }

        // CONTADORES GLOBAIS
        val nameCounters = mutableMapOf<String, Int>()

        allPokemon.forEach { pokemon ->

            val baseName =
                pokemon.nickname?.string?.takeIf { it.isNotBlank() }
                    ?: pokemon.species.name

            val current =
                nameCounters.getOrDefault(baseName, 0) + 1

            nameCounters[baseName] = current

            val sameNameCount = allPokemon.count {

                val otherBase =
                    it.nickname?.string?.takeIf { n -> n.isNotBlank() }
                        ?: it.species.name

                otherBase == baseName
            }

            val displayName =
                if (sameNameCount <= 1) {
                    baseName
                } else {
                    "$baseName#$current"
                }

            pokemonAliasMap[player.uuid]
                ?.put(displayName, pokemon.uuid)

            reversePokemonAliasMap[player.uuid]
                ?.put(pokemon.uuid, displayName)
        }

        val nearbyPlayers = player.server.playerList.players
            .filter { it.distanceTo(player) <= 20 && it != player }

        // --- RETRIEVAL AND MEMORIES LOGIC ---
        val keywords = moreText.lowercase()
            .split(Regex("[^a-zA-Z0-9áéíóúâêîôûãõç\\-]+"))
            .filter { it.length >= 3 }
            .toSet()

        val participatingPokemon = allPokemon.filter { it.currentHealth > 0 }
        val participantsUuids = participatingPokemon.map { it.uuid.toString() }.toSet()

        val candidateMemories = mutableSetOf<Memory>()
        participatingPokemon.forEach { p ->
            candidateMemories.addAll(MemorySystem.loadMemories(p.uuid.toString()))
        }

        val recentInteractions = serverLastInteractions[player.uuid] ?: emptyList()
        val currentTick = player.server.tickCount

        val scoredMemories = candidateMemories.map { m ->
            var score = 0
            val matchCount = m.keywords.count { it.lowercase() in keywords }
            score += matchCount

            val playerMsgWords = m.playerMessage.lowercase().split(Regex("[^a-zA-Z0-9áéíóúâêîôûãõç\\-]+")).filter { it.length >= 3 }.toSet()
            val playerMsgMatchCount = playerMsgWords.count { it in keywords }
            score += playerMsgMatchCount * 2

            val memoryWords = m.memory.lowercase().split(Regex("[^a-zA-Z0-9áéíóúâêîôûãõç\\-]+")).filter { it.length >= 3 }.toSet()
            val memoryMatchCount = memoryWords.count { it in keywords }
            score += memoryMatchCount

            val activeParticipantsCount = m.participants.count { it in participantsUuids }
            score += activeParticipantsCount * 2

            if (currentTick - m.createdTick < 100000) {
                score += 2
            }

            if (activeParticipantsCount > 1) {
                score += 1
            }

            // Check similarity with LAST_INTERACTIONS (recent summaries on server)
            val mWords = m.memory.lowercase().split(Regex("\\W+")).filter { it.length > 3 }.toSet()
            var overlapWithLastInteractions = false
            for (interaction in recentInteractions) {
                val interactionWords = interaction.lowercase().split(Regex("\\W+")).filter { it.length > 3 }.toSet()
                val intersection = mWords.intersect(interactionWords).size
                val union = mWords.union(interactionWords).size
                val jaccard = if (union > 0) intersection.toDouble() / union else 0.0
                if (jaccard > 0.5) {
                    overlapWithLastInteractions = true
                    break
                }

                val keywordsInInteraction = m.keywords.count { it in interaction.lowercase() }
                if (m.keywords.isNotEmpty() && keywordsInInteraction.toDouble() / m.keywords.size > 0.6) {
                    overlapWithLastInteractions = true
                    break
                }
            }

            if (overlapWithLastInteractions) {
                println("repetição detectada")//score -= 10
            }

            m to score
        }.sortedWith(compareByDescending<Pair<Memory, Int>> { it.second }.thenByDescending { it.first.createdTick })

        // 2. Diversity filter
        val selectedMemories = mutableListOf<Memory>()
        val skippedForDiversity = mutableListOf<Memory>()
        val selectedKeywords = mutableSetOf<String>()
        val selectedTexts = mutableListOf<String>()

        for ((m, score) in scoredMemories) {
            val words2 = m.memory.lowercase().split(Regex("\\W+")).filter { it.length > 3 }.toSet()
            val isTooSimilarText = selectedTexts.any { existingText ->
                val words1 = existingText.lowercase().split(Regex("\\W+")).filter { it.length > 3 }.toSet()
                val intersection = words1.intersect(words2).size
                val union = words1.union(words2).size
                val jaccard = if (union > 0) intersection.toDouble() / union else 0.0
                jaccard > 0.5
            }

            val overlapCount = m.keywords.count { it in selectedKeywords }
            val isTooSimilarKeywords = m.keywords.isNotEmpty() && (overlapCount.toDouble() / m.keywords.size > 0.6 || overlapCount >= 3)

            if (isTooSimilarText || isTooSimilarKeywords) {
                skippedForDiversity.add(m)
            } else {
                if (selectedMemories.size < config.maxRelevantMemories) {
                    selectedMemories.add(m)
                    selectedKeywords.addAll(m.keywords)
                    selectedTexts.add(m.memory)
                }
            }
        }

        // Fill remaining slots from skipped if we haven't reached the limit
        for (m in skippedForDiversity) {
            if (selectedMemories.size >= config.maxRelevantMemories) break
            selectedMemories.add(m)
        }


        fun resolveParticipantName(uuidStr: String): String {
            val uuid = try { UUID.fromString(uuidStr) } catch (e: Exception) { null } ?: return uuidStr.take(5)
            val alias = reversePokemonAliasMap[player.uuid]?.get(uuid)
            if (alias != null) return alias

            val partyPoke = PokemonQuery.getAllPokemon(player).find { it.uuid == uuid }
            if (partyPoke != null) {
                return partyPoke.nickname?.string?.takeIf { it.isNotBlank() } ?: partyPoke.species.name
            }
            
            val level = player.serverLevel()
            val entity = level.getEntity(uuid)
            if (entity is PokemonEntity) {
                val poke = entity.pokemon
                return poke.nickname?.string?.takeIf { it.isNotBlank() } ?: poke.species.name
            }

            return "Pokémon (${uuidStr.take(5)})"
        }

        return buildString {
            if (selectedMemories.isNotEmpty()) {
                appendLine("[RELEVANT MEMORIES]")
                selectedMemories.forEach { m ->
                    val partsStr = m.participants.joinToString(", ") { resolveParticipantName(it) }
                    appendLine("- Memory: ${m.memory} (Participants: $partsStr) | Keywords: ${m.keywords.joinToString(", ")}")
                }
                appendLine()
            }

            fun String?.takeIfUseful(): String? {
                val cleaned = this?.trim() ?: return null

                return when (cleaned.lowercase()) {
                    "", "null", "nenhum", "vazio", "false" -> null
                    else -> cleaned
                }
            }

            fun compactId(id: String?): String {
                if (id == null) return "unknown"

                return id
                    .substringAfterLast(":")
                    .substringAfterLast(".")
            }

            temporaryFeedback[player.uuid]
                ?.takeIf { it.isNotEmpty() }
                ?.let { feedbacks ->
                    appendLine("[RECENT FEEDBACK]")
                    feedbacks.forEach {
                        appendLine("- ${it.text}")
                    }
                    appendLine()
                }

            appendLine("[PLAYER IDS]")
            val ownerPreferred = PlayerNicknameManager.get(player.uuid, player.name.string)
            appendLine("$ownerPreferred (${player.name.string}) [OWNER]")

            nearbyPlayers.forEach { other ->
                val otherPreferred = PlayerNicknameManager.get(other.uuid, other.name.string)
                appendLine("$otherPreferred (${other.name.string})")
            }
            appendLine()

            // Injeta resumo da última sessão se existir
            val sessionSummary = CobblebrainWorldSave.getSessionSummary(player.uuid.toString())
            if (sessionSummary != null) {
                appendLine("[LAST SESSION RECAP]")
                appendLine(sessionSummary)
                appendLine()
            }

            appendLine(moreText)
            appendLine()

            // Injeta nota pendente (ex: abandono de quest) se houver
            val questNote = pendingQuestNote.remove(player.uuid)
            if (questNote != null) {
                appendLine("[IMPORTANT NOTE]")
                appendLine(questNote)
                appendLine()
            }
            
            // Quests
            val activeQuests = CobblebrainWorldSave.getActiveQuests(player)
            if (activeQuests.isNotEmpty() && config.outputQuests) {
                appendLine("[ACTIVE QUESTS]")
                activeQuests.forEach { q ->
                    val type = q.get("type").asString
                    val summary = q.get("questSummary")?.asString ?: "No summary"
                    val storyId = q.get("storyId")?.asString ?: "generic"
                    val slot = if (storyId != "generic") "STORY" else "SECONDARY"
                    
                    val goalInfo = when (type) {
                        "BATTLE" -> "Goal: Defeat ${q.get("targetSpecies").asString}"
                        "ITEM" -> "Goal: Collect ${q.get("amount").asInt}x ${q.get("target").asString}"
                        "ADVICE" -> {
                            val points = q.get("points")?.asInt ?: 0
                            val issue = q.get("issue")?.asString ?: "their problem"
                            "Goal: Help with advice about '$issue' | Progress: $points/5 | MANDATORY: Use #SCORE: +X or #SCORE: -X to reward/punish the player's advice"
                        }
                        "LOCATION", "TREASURE" -> {
                            val tx = q.get("targetX").asInt
                            val ty = q.get("targetY").asInt
                            val tz = q.get("targetZ").asInt
                            "Goal: Go to ($tx, $ty, $tz)"
                        }
                        else -> ""
                    }
                    
                    appendLine("- Type: $type | Slot: $slot | $goalInfo | Summary: $summary")
                }
                appendLine()
            }

            val genCandidateUuid = if (config.enableTraits && Random.nextDouble() < 0.01) {
                pokemons.filter {
                    val personality = MemorySystem.loadPersonality(it.uuid.toString())
                    (personality.traits.size < 2 || personality.quirks.size < 2) &&
                    (personality.traits.isNotEmpty() || personality.quirks.isNotEmpty())
                }.randomOrNull()?.uuid
            } else {
                null
            }

            appendLine("[ACTIVE POKEMON]")
            val nameCounters = mutableMapOf<String, Int>()
            if (pokemons.isEmpty()) {
                appendLine("No party Pokémon are currently active, so none will participate in the dialogue.")
            } else {
                pokemons.forEach { p ->
                    val allMoves = p.moveSet.getMoves().map { it.name }
                    val baseName = p.nickname?.string?.takeIf { it.isNotBlank() } ?: p.species.name
                    val currentCount = nameCounters.getOrDefault(baseName, 0) + 1
                    nameCounters[baseName] = currentCount

                    val sameNameCount = allPokemon.count {
                        val otherBase = it.nickname?.string?.takeIf { n -> n.isNotBlank() } ?: it.species.name
                        otherBase == baseName
                    }

                    val displayName = if (sameNameCount <= 1) {
                        baseName
                    } else {
                        "$baseName#$currentCount"
                    }

                    reversePokemonAliasMap.getOrPut(player.uuid) { mutableMapOf() }[p.uuid] = displayName
                    pokemonAliasMap.getOrPut(player.uuid) { mutableMapOf() }[displayName] = p.uuid

                    val parts = mutableListOf<String>()
                    parts += "Name: $displayName"
                    if (p.nickname != null) {
                        parts += "Species: ${p.species.name}"
                    }
                    parts += p.types.joinToString("/") { it.name }
                    parts += p.gender.toString()
                    if (p.currentHealth < p.maxHealth) {
                        parts += "HP: ${p.currentHealth}/${p.maxHealth}"
                    }
                    parts += "Lvl${p.level}"
                    val fullnessPercent = ((p.currentFullness.toFloat() / p.getMaxFullness().toFloat()) * 100f).toInt()
                    val hungerState = when {
                        fullnessPercent < 10 -> "very hungry"
                        fullnessPercent <= 30 -> "hungry"
                        fullnessPercent <= 50 -> "normal"
                        fullnessPercent <= 80 -> "satisfied"
                        else -> "completely full"
                    }
                    parts += "$hungerState ($fullnessPercent%)"
                    parts += "Nature: ${p.effectiveNature.name.path}"
                    if (allMoves.isNotEmpty()) {
                        parts += "Moves: ${allMoves.joinToString(",")}"
                    }
                    val friendshipPercent = ((p.friendship.toFloat() / config.maxFriendship.toFloat()) * 100).toInt()
                    parts += "Friendship: $friendshipPercent%"
                    if (p.entity?.isPokemonFlying == true) {
                        parts += "Flying"
                    }
                    if (p.entity?.passengers?.any { it is ServerPlayer } == true) {
                        parts += "Mounted"
                    }
                    if (PokemonQuery.isShoulderMounted(player, p)) {
                        parts += "Riding player shoulder"
                    }

                    appendLine(parts.joinToString(" | "))

                    // Load personality with displayName for lazy migration from Characteristics
                    val personality = MemorySystem.loadPersonality(p.uuid.toString(), displayName)

                    // Inline personality line: only emit if any field is non-empty
                    val personalityParts = mutableListOf<String>()
                    if (personality.about.isNotBlank()) personalityParts += "About: ${personality.about}"
                    if (personality.traits.isNotEmpty()) personalityParts += "Traits: ${personality.traits.joinToString(", ")}"
                    if (personality.quirks.isNotEmpty()) personalityParts += "Quirks: ${personality.quirks.joinToString(", ")}"
                    if (personality.likes.isNotEmpty()) personalityParts += "Likes: ${personality.likes.joinToString(", ")}"
                    if (personality.dislikes.isNotEmpty()) personalityParts += "Dislikes: ${personality.dislikes.joinToString(", ")}"
                    if (personalityParts.isNotEmpty()) {
                        appendLine(personalityParts.joinToString(" | "))
                    }

                    // A personality slot is free — only when enableTraits is on
                    if (config.enableTraits && p.uuid == genCandidateUuid) {
                        val maxTraits = 2
                        val maxQuirks = 2
                        val freeTraits = personality.traits.size < maxTraits
                        val freeQuirks = personality.quirks.size < maxQuirks
                        if (freeTraits || freeQuirks) {
                            appendLine("IMPORTANT: A personality slot is free. You may discover/generate a new long-term trait or quirk for $displayName.")
                            appendLine("Rules:")
                            val formats = mutableListOf<String>()
                            if (freeTraits) formats += "&TRAIT:$displayName:<new trait>"
                            if (freeQuirks) formats += "&QUIRK:$displayName:<new quirk>"
                            appendLine("- Format: Output at the end of your response: ${formats.joinToString(" or ")}")
                            appendLine("- Consider the Pokémon's Nature (${p.effectiveNature.name.path}), but also allow unique individual characteristics.")
                            appendLine("- Do not duplicate or use traits/quirks very similar to existing ones.")
                            appendLine()
                        }
                    }

                    // Karma
                    val speciesName = p.species.name
                    val playerUUID = player.stringUUID
                    if (playerUUID != null) {
                        val karmaRoot = CobblebrainWorldSave.data.getAsJsonObject("karma")
                        if (karmaRoot.has(playerUUID)) {
                            val playerKarma = karmaRoot.getAsJsonObject(playerUUID)
                            if (playerKarma.has(speciesName)) {
                                val karmaValue = playerKarma.get(speciesName).asInt
                                appendLine("Karma: $karmaValue")
                            }
                        }
                        val killRoot = CobblebrainWorldSave.data.getAsJsonObject("kill_count")
                        if (killRoot.has(player.stringUUID)) {
                            val playerKills = killRoot.getAsJsonObject(player.stringUUID)
                            playerKills.entrySet().forEach { entry ->
                                val species = entry.key
                                val killValue = entry.value.asInt
                                appendLine("The player killed x$killValue $species")
                            }
                            killRoot.add(player.stringUUID, JsonObject())
                            CobblebrainWorldSave.save()
                        }
                    }
                }
            }
            appendLine()

            appendLine("[NEARBY POKEMON]")
            if (context.nearbyPokemon.takeIfUseful() != null) {
                appendLine(context.nearbyPokemon)
            } else {
                appendLine("No nearby wild Pokémon are present, so none will participate in the dialogue.")
            }
            appendLine()

            if (config.wildPokemonTalkChance > 0.0
                && Random.nextDouble() <= config.wildPokemonTalkChance
                && context.nearbyPokemonEntities.isNotEmpty()
            ) {
                val wildEntity = context.nearbyPokemonEntities.randomOrNull()
                if (wildEntity != null) {
                    val giver = wildEntity.pokemon
                    val activeQuestsList = CobblebrainWorldSave.getActiveQuests(player)
                    val hasActiveQuest = activeQuestsList.any { it.get("status").asString == "IN_PROGRESS" }
                    if (!hasActiveQuest && !OfflinePlayers.isOffline(player.uuid) && Random.nextDouble() <= config.wildQuestChance) {
                        val roll = Random.nextInt(4)
                        when (roll) {
                            0 -> {
                                CobblebrainWorldSave.createAdviceQuest(player, wildEntity)
                                val secondaryQuests = CobblebrainWorldSave.getSecondaryQuests(player)
                                val adviceQuest = secondaryQuests.last()
                                val issue = adviceQuest.get("issue")?.asString ?: "something"
                                val name = giver.nickname?.string ?: giver.species.resourceIdentifier.path
                                appendLine("IMPORTANT: $name has started an ADVICE quest about $issue! It wants to talk with the player or their Pokémon team about this specific topic!")
                                player.sendSystemMessage(Component.translatable("cobblebrain.quest.started.advice", name, issue).withStyle(ChatFormatting.YELLOW))
                            }
                            1 -> {
                                CobblebrainWorldSave.createItemQuest(player, wildEntity)
                                val secondaryQuests = CobblebrainWorldSave.getSecondaryQuests(player)
                                val itemQuest = secondaryQuests.last()
                                val targetItem = itemQuest.get("target").asString
                                val amount = itemQuest.get("amount").asInt
                                val name = giver.nickname?.string ?: giver.species.resourceIdentifier.path
                                appendLine("IMPORTANT: $name has started an ITEM quest! It needs the player or their Pokémon team to gather x$amount $targetItem! It wants to talk with the player or their Pokémon team!")
                                player.sendSystemMessage(Component.translatable("cobblebrain.quest.started.item", name).withStyle(ChatFormatting.YELLOW))
                            }
                            2 -> {
                                CobblebrainWorldSave.createBattleQuest(player, wildEntity)
                                val secondaryQuests = CobblebrainWorldSave.getSecondaryQuests(player)
                                val battleQuest = secondaryQuests.last()
                                val targetSpecies = battleQuest.get("targetSpecies").asString
                                val name = giver.nickname?.string ?: giver.species.resourceIdentifier.path
                                appendLine("IMPORTANT: $name has started a BATTLE quest! It wants the player or their Pokémon team to defeat a $targetSpecies in a pokemon battle! It wants to talk with the player or their Pokémon team!")
                                player.sendSystemMessage(Component.translatable("cobblebrain.quest.started.battle", name).withStyle(ChatFormatting.YELLOW))
                            }
                            3 -> {
                                CobblebrainWorldSave.createTreasureQuest(player, wildEntity)
                                val name = giver.nickname?.string ?: giver.species.resourceIdentifier.path
                                appendLine("IMPORTANT: $name has lost its item storage, found a hidden stash, or heard rumors of items hidden by another Pokémon! It wants the player to find the barrel containing these items!")
                                player.sendSystemMessage(Component.translatable("cobblebrain.quest.started.treasure", name).withStyle(ChatFormatting.YELLOW))
                            }
                        }
                    } else if (!hasActiveQuest) {
                        appendLine("IMPORTANT: Wild Nearby pokemons (not on the team) are talking about something!")
                    }
                }
                appendLine()
            }

            appendLine("[UNAVAILABLE POKEMON]")
            val activeUuids = pokemons.map { it.uuid }.toSet()
            val allPartyPokemon = PokemonQuery.getAllPokemon(player)
            val unavailableCount = allPartyPokemon.count { it.uuid !in activeUuids }
            if (unavailableCount == 0) {
                appendLine("none")
            } else {
                allPartyPokemon.forEach { p ->
                    if (p.uuid in activeUuids) {
                        return@forEach
                    }
                    val name = p.nickname?.string ?: p.species.name
                    when {
                        p.currentHealth <= 0 -> {
                            appendLine("$name is FAINTED, unable to talk or interact.")
                        }
                        else -> {
                            appendLine("$name is STORED, unable to talk or interact.")
                        }
                    }
                }
            }
            appendLine()

            appendLine("[WORLD CONTEXT]")
            if (config.outputWorldContext) {
                val worldParts = mutableListOf<String>()
                worldParts += compactId(context.biome)
                context.weather.let { worldParts += it }
                context.dimension.let { worldParts += it }
                context.timeLabel.let { worldParts += it }
                appendLine("World: ${worldParts.joinToString(" | ")}")
                if (!config.lowTokenMode) {
                    appendLine("Ground: ${compactId(context.blockUnder)}")
                }
            }

            if (!config.lowTokenMode && config.outputWorldContext) {
                context.specialBlocks.takeIfUseful()?.let {
                    appendLine("Special blocks: $it")
                }
            }

            if (!config.lowTokenMode && config.outputMobsContext) {
                context.nearbyMobs.takeIfUseful()?.let {
                    appendLine("Nearby mobs: $it")
                }
            }

            if (!config.lowTokenMode && config.outputWorldContext) {
                context.nearbyItems.takeIfUseful()?.let { items ->
                    val compacted = items
                        .split(",")
                        .map { compactId(it.trim()) }
                    appendLine("Ground items: ${compacted.joinToString(", ")}")
                }
            }

            if (config.outputWorldContext) {
                val playerParts = mutableListOf<String>()
                playerParts += "HP ${context.health}/${context.maxHealth}"
                context.mainHand.takeIfUseful()?.let {
                    playerParts += "Main hand: ${compactId(it)}"
                }
                appendLine("Player: ${playerParts.joinToString(" | ")}")
            }
            appendLine()

            appendLine("[FRIENDSHIP]")
            appendLine("plus=${config.increaseFriendship}")
            appendLine("minus=${config.decreaseFriendship}")

            if (config.enableTraits) {
                val needInit = pokemons.filter { p ->
                    val personality = MemorySystem.loadPersonality(p.uuid.toString())
                    personality.traits.isEmpty() && personality.quirks.isEmpty()
                }
                if (needInit.isNotEmpty()) {
                    appendLine()
                    appendLine()
                    appendLine("IMPORTANT:")
                    appendLine()
                    appendLine("The following party Pokémon have no established traits or quirks:")
                    appendLine()

                    needInit.forEach { p ->
                        val displayName =
                            reversePokemonAliasMap[player.uuid]?.get(p.uuid)
                                ?: p.nickname?.string?.takeIf { it.isNotBlank() }
                                ?: p.species.name

                        appendLine("- $displayName")
                    }

                    appendLine()
                    appendLine("Generate exactly:")
                    appendLine("- 1 Trait per Pokémon")
                    appendLine("- 1 Quirk per Pokémon")
                    appendLine()
                    appendLine("Rules:")
                    appendLine("- Traits and quirks are part of identity, not the entire personality.")
                    appendLine("- Prefer preferences, fears, habits, opinions, reactions, or recurring behaviors.")
                    appendLine("- Quirks must be discoverable through dialogue and interactions.")
                    appendLine("- NEVER use visual-only, sound-only, animation-only, or body-language quirks.")
                    appendLine("- NEVER generate a trait or quirk that already exists for that Pokémon.")
                    appendLine()
                    appendLine("Format:")
                    appendLine("&TRAIT:<PokemonName>:<trait>")
                    appendLine("&QUIRK:<PokemonName>:<quirk>")
                }
            }
        }.trim()
    }

    private fun isPromptArtifact(line: String): Boolean {

        val normalized = line.trim().lowercase()

        // Se parece um diálogo ("Nome: fala"), nunca filtra.
        if (":" in normalized)
            return false

        return normalized.startsWith("separator") ||
                normalized.startsWith("quests") ||
                normalized.startsWith("types") ||
                normalized.startsWith("response language") ||
                normalized.startsWith("dialogue format") ||
                normalized.startsWith("friendship format") ||
                normalized.startsWith("memory format") ||
                normalized.startsWith("action format") ||
                normalized.startsWith("guaranteed catch format") ||
                normalized.startsWith("resume format") ||
                normalized.startsWith("trait format") ||
                normalized.startsWith("quirk format") ||
                normalized.startsWith("general rules") ||
                normalized.startsWith("quest system") ||
                normalized.startsWith("quest completed")
    }

    fun checkIaResponse(server: MinecraftServer, player: ServerPlayer, content: String) {
        val last = lastResponseContent[player.uuid]
        if (content.isBlank() || content == last) return

        // Clear quest waiting states when response arrives
        isWaitingForQuestResponse[player.uuid] = false
        pendingInterruption[player.uuid] = false

        // Intercepta se for a resposta do resumo
        if (content.startsWith("[SUMMARY_RESPONSE]")) {
            val summary = content.replace("[SUMMARY_RESPONSE]", "").trim()
            
            // Se houver erro na geração, apenas avisa o jogador e não salva
            if (summary.startsWith("Failed", ignoreCase = true) || summary.startsWith("Error", ignoreCase = true)) {
                player.sendSystemMessage(Component.translatable("cobblebrain.summary.error").withStyle(ChatFormatting.RED)
                    .append(Component.literal(summary).withStyle(ChatFormatting.GRAY)))
                return
            }

            CobblebrainWorldSave.setSessionSummary(player.uuid.toString(), summary)
            player.sendSystemMessage(Component.translatable("cobblebrain.summary.saved").withStyle(ChatFormatting.GREEN))
            return
        }

        // Se chegamos aqui, é uma resposta normal (diálogo).
        // Se houver um resumo, mostramos o aviso antes de apagar
        val currentSummary = CobblebrainWorldSave.getSessionSummary(player.uuid.toString())
        if (currentSummary != null) {
            player.sendSystemMessage(Component.literal("\n"))
            player.sendSystemMessage(
                Component.translatable("cobblebrain.summary.previously")
                    .withStyle(ChatFormatting.GOLD)
                    .append(Component.translatable("cobblebrain.summary.applied").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC))
            )
            player.sendSystemMessage(Component.literal("\n"))
            
            // Apagamos o resumo da última sessão pois a IA já o "consumiu" no prompt.
            CobblebrainWorldSave.clearSessionSummary(player.uuid.toString())
        }

        // Linhas para chat (falas + friendship), excluindo memórias
        val headerCleanupRegex = Regex(
            """^\s*\[?\s*(DIALOGUE FORMAT|FRIENDSHIP FORMAT|MEMORY FORMAT|ACTION FORMAT|GUARANTEED CATCH FORMAT|RESUME FORMAT|QUEST SYSTEM|QUEST COMPLETED|GENERAL RULES|TRAITS AND QUIRKS FORMAT|TRAIT FORMAT|QUIRK FORMAT)\s*]?\s*:?\s*""",
            RegexOption.IGNORE_CASE
        )

        // 1. Extract and process &MEMORY blocks, and remove them from content to avoid pollution
        val memoryRegex = Regex("""&MEMORY:([^:]+):([^|]+)\|([^|]+)""", RegexOption.IGNORE_CASE)
        val traitRegex = Regex("""&TRAIT:([^:]+):([^|\n%#&]+)""", RegexOption.IGNORE_CASE)
        val quirkRegex = Regex("""&QUIRK:([^:]+):([^|\n%#&]+)""", RegexOption.IGNORE_CASE)
        var cleanedContent = content

        traitRegex.findAll(content).forEach { match ->
            val fullMatch = match.value
            cleanedContent = cleanedContent.replace(fullMatch, "")

            if (!config.enableTraits) return@forEach

            val pokemonName = match.groupValues[1].trim()
            val traitText = match.groupValues[2].trim()

            if (traitText.isNotBlank()) {
                val aliases = pokemonAliasMap[player.uuid] ?: emptyMap()
                var uuid = aliases[pokemonName] ?: aliases.entries.firstOrNull { it.key.equals(pokemonName, ignoreCase = true) }?.value
                if (uuid == null) {
                    val cleaned = pokemonName.substringBefore("#").trim()
                    uuid = aliases.entries.firstOrNull { it.key.substringBefore("#").equals(cleaned, ignoreCase = true) }?.value
                }

                if (uuid != null) {
                    val personality = MemorySystem.loadPersonality(uuid.toString())
                    if (personality.traits.size < 2) {
                        val isDup = personality.traits.any { existing ->
                            existing.equals(traitText, ignoreCase = true) ||
                            existing.lowercase().contains(traitText.lowercase()) ||
                            traitText.lowercase().contains(existing.lowercase())
                        }
                        if (!isDup) {
                            personality.traits.add(traitText)
                            MemorySystem.savePersonality(uuid.toString(), personality)
                        }
                    }
                }
            }
        }

        quirkRegex.findAll(content).forEach { match ->
            val fullMatch = match.value
            cleanedContent = cleanedContent.replace(fullMatch, "")

            if (!config.enableTraits) return@forEach

            val pokemonName = match.groupValues[1].trim()
            val quirkText = match.groupValues[2].trim()

            if (quirkText.isNotBlank()) {
                val aliases = pokemonAliasMap[player.uuid] ?: emptyMap()
                var uuid = aliases[pokemonName] ?: aliases.entries.firstOrNull { it.key.equals(pokemonName, ignoreCase = true) }?.value
                if (uuid == null) {
                    val cleaned = pokemonName.substringBefore("#").trim()
                    uuid = aliases.entries.firstOrNull { it.key.substringBefore("#").equals(cleaned, ignoreCase = true) }?.value
                }

                if (uuid != null) {
                    val personality = MemorySystem.loadPersonality(uuid.toString())
                    if (personality.quirks.size < 2) {
                        val isDup = personality.quirks.any { existing ->
                            existing.equals(quirkText, ignoreCase = true) ||
                            existing.lowercase().contains(quirkText.lowercase()) ||
                            quirkText.lowercase().contains(existing.lowercase())
                        }
                        if (!isDup) {
                            personality.quirks.add(quirkText)
                            MemorySystem.savePersonality(uuid.toString(), personality)
                        }
                    }
                }
            }
        }
        
        memoryRegex.findAll(content).forEach { match ->
            val fullMatch = match.value
            cleanedContent = cleanedContent.replace(fullMatch, "")

            val namesStr = match.groupValues[1].trim()
            val memoryText = match.groupValues[2].trim()
            val keywordsStr = match.groupValues[3].trim()

            val names = namesStr.split(",").map { it.trim() }
            val uuids = names.mapNotNull { name ->
                val aliases = pokemonAliasMap[player.uuid] ?: emptyMap()
                var uuid = aliases[name]
                if (uuid == null) {
                    uuid = aliases.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
                }
                if (uuid == null) {
                    val cleaned = name.substringBefore("#").trim()
                    uuid = aliases.entries.firstOrNull { it.key.substringBefore("#").equals(cleaned, ignoreCase = true) }?.value
                }
                uuid
            }

            if (uuids.isNotEmpty()) {
                val participantsList = uuids.map { it.toString() }
                val keywordsList = keywordsStr.split(",")
                    .map { it.trim().lowercase() }
                    .filter { it.isNotEmpty() }
                
                val memoryObj = Memory(
                    participants = participantsList,
                    memory = memoryText,
                    keywords = keywordsList,
                    createdTick = server.tickCount.toLong(),
                    playerMessage = lastPlayerMessage[player.uuid] ?: ""
                )

                uuids.forEach { uuid ->
                    MemorySystem.saveMemory(uuid.toString(), memoryObj)
                }
            }
        }

        val allLines = cleanedContent.split("|")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

            // remove headers automaticos
            .map { line ->
                line.replace(headerCleanupRegex, "").trim()
            }

            .filter { it.isNotEmpty() }

        val falas = allLines.filterNot {
            isPromptArtifact(it) ||

                    it.startsWith("@") ||
                    it.startsWith("#") ||
                    it.startsWith("&") ||
                    it.startsWith("=") ||
                    it.startsWith("!RESUME") ||
                    (it.startsWith("%") && !it.contains(":")) ||
                    (!config.showFriendship &&
                            (it.startsWith("friendship", ignoreCase = true) ||
                                    (it.startsWith("%") && it.contains(":"))))
        }

        val commandLines = allLines.filter { it.startsWith("#") }
        val summaryLines = allLines.filter { it.startsWith("&", ignoreCase = true) }

        val resumeLines = cleanedContent.split("|")
            .map { it.trim() }
            .filter { it.startsWith("!RESUME", ignoreCase = true) || it.startsWith("=") }

        resumeLines.forEach { line ->
            val resumeText = if (line.startsWith("!RESUME", ignoreCase = true)) {
                line.substringAfter("!RESUME").removePrefix(":").trim()
            } else {
                line.removePrefix("=").trim()
            }
            if (resumeText.isNotBlank()) {
                val list = serverLastInteractions.getOrPut(player.uuid) { mutableListOf() }
                list.add(resumeText)
                while (list.size > 15) {
                    list.removeAt(0)
                }
            }
        }

        // 2. Detecta quest summary
        summaryLines.forEach { line ->

        val summaryText = line
            .substringAfter("&")
            .removePrefix(":")
            .trim()

        if (summaryText.isBlank()) return@forEach

        val activeQuests = CobblebrainWorldSave.getActiveQuests(player)
        val lastQuest = activeQuests.firstOrNull { it.get("status")?.asString == "IN_PROGRESS" }

        if (lastQuest != null) {
            lastQuest.addProperty("questSummary", summaryText)
            CobblebrainWorldSave.save()}
        }


        // 3. Detecta !CATCH: Name para captura garantizada
        if (content.contains("!CATCH") || allLines.any { it.startsWith("!") && !it.startsWith("!RESUME") && !it.startsWith("!CATCH") }) {
            val nameMatch = Regex("""!CATCH:\s*([^|%\n]+)""").find(content)
            val pokemonName = if (nameMatch != null) {
                nameMatch.groupValues[1].trim()
            } else {
                val catchLine = allLines.firstOrNull { it.startsWith("!") && !it.startsWith("!RESUME") && !it.startsWith("!CATCH") }
                catchLine?.removePrefix("!")?.trim() ?: ""
            }
            
            val level = player.serverLevel()
            val pokemon = if (pokemonName.isNotEmpty()) {
                level.getEntitiesOfClass(PokemonEntity::class.java, player.boundingBox.inflate(16.0)) {
                    it.displayName?.string?.contains(pokemonName, ignoreCase = true) == true ||
                    it.pokemon.species.name.contains(pokemonName, ignoreCase = true)
                }.minByOrNull { it.distanceTo(player) }
            } else {
                level.getEntitiesOfClass(PokemonEntity::class.java, player.boundingBox.inflate(10.0))
                    .minByOrNull { it.distanceTo(player) }
            }

            pokemon?.let {
                it.addTag("cobblebrain:guaranteed_${player.uuid}")
                
                // Feedback Visual: Nome fica VERDE
                val originalName = it.displayName?.string
                it.customName = Component.literal(originalName).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                it.isCustomNameVisible = true
                
                player.sendSystemMessage(
                    Component.translatable("cobblebrain.pokemon.convinced", originalName)
                        .withStyle(ChatFormatting.GREEN, ChatFormatting.ITALIC)
                )
            }
        }

        // 4. Detecta ações (#)
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
        val newRegex = Regex(
            """%\s*([\w\s.'♀♂-]+)\s*:\s*([+-])\s*(\d+)""",
            RegexOption.IGNORE_CASE
        )
        val matches = regex.findAll(content)
        val newMatches = newRegex.findAll(content)

        fun applyFriendship(nomePokemon: String, sinal: String, incrementoValor: Double) {
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

        for (match in matches) {
            applyFriendship(match.groupValues[1], match.groupValues[3], match.groupValues[4].toDouble())
        }
        for (match in newMatches) {
            applyFriendship(match.groupValues[1], match.groupValues[2], match.groupValues[3].toDouble())
        }

        // Consome feedback temporario apos resposta valida
        temporaryFeedback[player.uuid]
            ?.let { feedbacks ->

                feedbacks.forEach {
                    it.remainingUses--
                }

                feedbacks.removeIf {
                    it.remainingUses <= 0
                }

                if (feedbacks.isEmpty()) {
                    temporaryFeedback.remove(player.uuid)
                }
            }

        lastResponseContent[player.uuid] = content

        // 5. Agenda mensagens para o jogador que falou
        lastResponseContent[player.uuid] = content
        val startTick = server.tickCount.toLong()

        // ADVICE score tracking
        handleAdviceQuestResponse(player, null, content)

        // Monta todas as falas do novo diálogo e associa o pitchMod
        val novasMensagens = mutableListOf<ScheduledMessage>()
        
        falas.forEachIndexed { i, line ->
            val speakerName = line.substringBefore(":").trim()
            val speaker = PokemonQuery.findActivePokemon(player)
                .firstOrNull { it.species.name.equals(speakerName, ignoreCase = true) || it.nickname?.string?.equals(speakerName, ignoreCase = true) == true }

            var mod = 0f
            val lineIndexInAll = allLines.indexOf(line)
            if (lineIndexInAll != -1 && lineIndexInAll + 1 < allLines.size) {
                val nextLine = allLines[lineIndexInAll + 1]
                var match = Regex("""friendship\s+([\w\s.'♀♂-]+):\s*([\d.,]+)\s*([+-])\s*(-?\d+)""", RegexOption.IGNORE_CASE).find(nextLine)
                var isNewFormat = false
                if (match == null) {
                    match = Regex("""%\s*([\w\s.'♀♂-]+)\s*:\s*([+-])\s*(\d+)""", RegexOption.IGNORE_CASE).find(nextLine)
                    isNewFormat = true
                }
                if (match != null) {
                    val targetName = match.groupValues[1].trim()
                    if (targetName.equals(speakerName, ignoreCase = true)) {
                        val sinal = if (isNewFormat) match.groupValues[2] else match.groupValues[3]
                        val valor = (if (isNewFormat) match.groupValues[3] else match.groupValues[4]).toFloat()
                        // 1 ponto = 0.03 de pitch (limite de +/- 0.18)
                        mod = (if (sinal == "-") -valor else valor) * 0.03f
                        mod = mod.coerceIn(-0.18f, 0.18f)
                    }
                }
            }

            novasMensagens.add(ScheduledMessage(
                player = player,
                text = line,
                sendAtTick = if (i == 0) startTick else startTick + (i * 100),
                speaker = speaker,
                pitchMod = mod
            ))
        }

        // Substitui qualquer diálogo anterior por este novo conjunto
        scheduledMessages[player.uuid] = novasMensagens.toMutableList()

    }

    fun triggerSessionSummary(player: ServerPlayer) {
        if (OfflinePlayers.isOffline(player.uuid)) {
            player.sendSystemMessage(Component.translatable("cobblebrain.summary.offline_unavailable").withStyle(ChatFormatting.RED))
            return
        }
        player.sendSystemMessage(Component.translatable("cobblebrain.summary.saving").withStyle(ChatFormatting.YELLOW))

        val nearbyPokemon = currentServer?.playerList?.players?.flatMap { PokemonQuery.findActivePokemon(it) }
            ?.filter { (it.entity?.distanceTo(player) ?: 1000.0f) < 32f } ?: emptyList()

        val contextData = buildPrompt(player, nearbyPokemon, "[SESSION SUMMARY REQUEST]")
        
        sendToPlayerSummary?.invoke(player, contextData)
    }

    var sendToPlayerSummary: ((ServerPlayer, String) -> Unit)? = null
}
