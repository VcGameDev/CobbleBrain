package vito.cobblebrain.social

import AIHandler
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.battles.BattleFledEvent
import com.cobblemon.mod.common.api.events.battles.BattleStartedPostEvent
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent
import com.cobblemon.mod.common.api.events.pokemon.PokemonSentPostEvent
import com.cobblemon.mod.common.battles.BattleRegistry
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import kotlin.random.Random
import com.cobblemon.mod.common.pokemon.Pokemon
import com.google.gson.Gson
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.ChatFormatting
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvent
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundSource
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.decoration.ArmorStand
import vito.cobblebrain.config.CobblebrainConfig
import vito.cobblebrain.currentServer
import vito.cobblebrain.sensors.CommandState
import vito.cobblebrain.sensors.MemoryStore.loadPokemonMemories
import vito.cobblebrain.sensors.MemoryStore.savePokemonMemory
import vito.cobblebrain.sensors.collectWorldContext
import vito.cobblebrain.sensors.parseCommand

import java.io.File
import java.lang.Math.toDegrees
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.atan2

object DialogueSystem {
    val gson = Gson()
    val configFile = File("config/cobblebrain.json")
    val config: CobblebrainConfig = gson.fromJson(configFile.readText(), CobblebrainConfig::class.java)

    private val scheduledMessages = mutableListOf<ScheduledMessage>()

    private data class ScheduledMessage(
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


    fun register() {
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            val player = handler.player

            // Instrução sobre o comando
            player.sendSystemMessage(
                Component.literal("Welcome to Cobblebrain! Use the command ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal("/msgpk <mensagem>").withStyle(ChatFormatting.AQUA))
                    .append(" to talk to the pokemons.")
            )

            // Lembrete sobre config
            player.sendSystemMessage(
                Component.literal("customize the mod (and its language) as you wish in ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal("cobblebrain.json").withStyle(ChatFormatting.AQUA))
                    .append(" in run/config.")
            )
        }

        // Evento de chat: intercepta mensagens normais
        if (config.listenToChat) {
            ServerMessageEvents.CHAT_MESSAGE.register { message, sender, _ ->
                val conteudo = message.signedContent()

                // Se onlyNearbyChat estiver ativo, só processa se houver alguém perto
                if (config.onlyNearbyChat) {
                    val hasNearby = sender.server.playerList.players.any { other ->
                        other != sender && other.distanceTo(sender) <= 15.0 // raio de 15 blocos
                    }
                    if (!hasNearby) {
                        return@register // não agenda nada se não tiver ninguém perto
                    }
                }
                onPlayerChat(sender, conteudo)
            }
        }

        CobblemonEvents.BATTLE_STARTED_POST.subscribe { event: BattleStartedPostEvent ->
            val battle = event.battle
            val server = battle.players.firstOrNull()?.server ?: return@subscribe
            if (config.dialogueOnBattle) {
                server.execute {
                    scheduledMessages.clear()
                    val ativos = battle.activePokemon.mapNotNull { it.battlePokemon }

                    if (ativos.size < 2) {
                        println("Battle started, but no active Pokémon detected yet.")
                        return@execute
                    }

                    // separa Pokémon com dono (jogador) e sem dono (selvagem)
                    val meus = ativos.filter { it.originalPokemon.getOwnerUUID() != null }
                    val inimigos = ativos.filter { it.originalPokemon.getOwnerUUID() == null }

                    val player = meus.firstOrNull()?.originalPokemon?.let {
                        val ownerId = it.getOwnerUUID() ?: return@execute
                        server.playerList.getPlayer(ownerId)
                    } ?: return@execute

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
                        "IMPORTANT: I started a battle with my team[$meusNomes] against [$inimigosNomes]"
                    )
                    File("cobblebrain-ai/comando_ia.txt").writeText(prompt)
                    println("IMPORTANT: I started a battle with my team[$meusNomes] against [$inimigosNomes]")
                }
            }
        }

        CobblemonEvents.POKEMON_SENT_POST.subscribe { event: PokemonSentPostEvent ->
            val pokemon = event.pokemon
            val ownerId = pokemon.getOwnerUUID() ?: return@subscribe

            val battle = BattleRegistry.getBattleByParticipatingPlayerId(ownerId)
            if (battle != null && config.dialogueOnBattle) {
                scheduledMessages.clear()

                val nickname = pokemon.nickname
                val species = pokemon.species.name
                val level = pokemon.level
                val ownerPlayer = pokemon.getOwnerPlayer() ?: return@subscribe

                // regra: se nickname != null → usa species, senão usa nickname
                val displayName = if (nickname != null) species else nickname ?: species

                // se o dono do Pokémon for o próprio jogador local → "eu"
                val playerNameForPrompt = if (ownerPlayer == currentServer?.playerList?.getPlayer(ownerId)) {
                    "I"
                } else {
                    ownerPlayer.name.string
                }

                val ativos = PokemonQuery.findActivePokemon(ownerPlayer)

                println("During the battle, $playerNameForPrompt sent $displayName (Lv.$level) to fight!")
                val prompt = buildPrompt(
                    ownerPlayer,
                    ativos,
                    "IMPORTANT: During the battle, $playerNameForPrompt sent $displayName (Lv.$level) to fight!"
                )
                File("cobblebrain-ai/comando_ia.txt").writeText(prompt)
            }
        }


        CobblemonEvents.BATTLE_FLED.subscribe { event: BattleFledEvent ->
            val actor = event.player // PlayerBattleActor que fugiu
            val uuids = actor.getPlayerUUIDs()
            if (config.dialogueOnBattle) {
                if (uuids.isEmpty()) {
                    println("${actor.getName().string} fled from battle (not human player).")
                    return@subscribe
                }

                val server = currentServer ?: return@subscribe

                uuids.forEach { uuid ->
                    val player = server.playerList.getPlayer(uuid)
                    if (player != null) {
                        val ativos = PokemonQuery.findActivePokemon(player)
                        val prompt = buildPrompt(
                            player,
                            ativos,
                            "IMPORTANT: we run away from the battle"
                        )
                        File("cobblebrain-ai/comando_ia.txt").writeText(prompt)
                        println("${player.name.string} we run away from the battle")
                    }
                }
            }
        }

        CobblemonEvents.BATTLE_VICTORY.subscribe { event: BattleVictoryEvent ->
            val battle = event.battle
            if (config.dialogueOnBattle) {
                for (player in battle.players) {
                    // encontra o ator pelo UUID do jogador
                    val myActor = battle.actors.firstOrNull { it.uuid == player.uuid } ?: continue

                    if (event.winners.contains(myActor)) {
                        scheduledMessages.clear()
                        println("We won the battle")
                        val ativos = PokemonQuery.findActivePokemon(player)
                        val prompt = buildPrompt(player, ativos, "IMPORTANT: We won the battle")
                        File("cobblebrain-ai/comando_ia.txt").writeText(prompt)
                    } else {
                        scheduledMessages.clear()
                        val ativos = PokemonQuery.findActivePokemon(player)
                        val prompt = buildPrompt(player, ativos, "IMPORTANT: We lost the battle")
                        File("cobblebrain-ai/comando_ia.txt").writeText(prompt)

                    }
                }
            }
        }

        ServerLivingEntityEvents.AFTER_DAMAGE.register { entity, source, amount, newHealth, absorbed ->
            when (entity) {
                is ServerPlayer -> {
                    val now = System.currentTimeMillis()
                    val last = lastPrompt[entity.uuid] ?: 0L
                    if (now - last >= 18000 && config.dialogueOnDamage) {
                        scheduledMessages.clear()
                        lastPrompt[entity.uuid] = now
                        val cause = source.msgId
                        println("IMPORTANT: I took $amount of damage from $cause. Final health: $newHealth")
                        val ativos = PokemonQuery.findActivePokemon(entity)
                        val prompt = buildPrompt(entity, ativos, "IMPORTANT: I took $amount of damage from $cause. Final health: $newHealth")
                        File("cobblebrain-ai/comando_ia.txt").writeText(prompt)
                    }
                }
                is PokemonEntity -> {
                    val ownerUuid = entity.pokemon.getOwnerUUID()
                    if (ownerUuid != null && config.dialogueOnDamage) {
                        val server = entity.server ?: return@register
                        val owner: ServerPlayer? = server.playerList.getPlayer(ownerUuid)

                        if (owner != null) {
                            val now = System.currentTimeMillis()
                            val last = lastPrompt[owner.uuid] ?: 0L
                            if (now - last >= 18000) {
                                scheduledMessages.clear()
                                lastPrompt[owner.uuid] = now
                                val cause = source.msgId
                                val pokemonNickname = entity.pokemon.nickname?.string
                                val pokemonSpecies = entity.pokemon.species.name

                                // se nickname for null ou vazio, usa species
                                val pokemonName = if (pokemonNickname.isNullOrBlank()) pokemonSpecies else pokemonNickname

                                println("My Pokémon $pokemonName took $amount of damage from $cause.")
                                server.playerList.getPlayer(ownerUuid)?.let { owner ->
                                    val ativos = PokemonQuery.findActivePokemon(owner)
                                    val prompt = buildPrompt(owner, ativos, "My Pokémon $pokemonName took $amount of damage from $cause.")
                                    File("cobblebrain-ai/comando_ia.txt").writeText(prompt)
                                }
                            }
                        }
                    }
                }
            }
        }

        ServerTickEvents.END_SERVER_TICK.register { server ->
            flushScheduledMessages(server)
            tickBubbles(currentServer!!)

            // mantém o olhar fixo enquanto durar o foco
            maintainLookAt(server)

            if (server.tickCount % 200 == 0 && scheduledMessages.isEmpty()) {
                runSocialTick(server)
            }

            if (server.tickCount % 70 == 0) {
                checkIaResponse(server)
            }

            if (server.tickCount % 20 == 0) {
                ensureChatRunning(server)
            }
        }
    }

    private var chatThread: Thread? = null

    fun ensureChatRunning(player: MinecraftServer?) {
        if (chatThread == null || !chatThread!!.isAlive) {
            println("[CobbleBrain] AI not running, restarting...")
            chatThread = Thread {
                try {
                    val chave = config.apiKey
                    if (chave.isBlank()) {
                        println("[CobbleBrain] No API key configured! Edit 'apiKey' in config/cobblebrain.json")
                        val msg = Component.literal("§c[CobbleBrain] No API key configured! Edit 'apiKey' in config/cobblebrain.json")
                        player?.sendSystemMessage(msg)
                        return@Thread
                    }

                    val chat = AIHandler("cobblebrain-ai")
                    chat.start()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            chatThread!!.start()
        }
    }

    private const val PROMPT_COOLDOWN = 40L // ~2 segundos (40 ticks)

    val lastPromptTicks = mutableMapOf<UUID, Long>()

    private var lastSpeakerPlayer: ServerPlayer? = null

    fun onPlayerChat(player: ServerPlayer, text: String) {
        val now = player.server.tickCount.toLong()
        val last = lastPromptTicks[player.uuid] ?: 0L
        val diff = now - last

        if (diff < PROMPT_COOLDOWN) {
            val wait = ((PROMPT_COOLDOWN - diff) / 20)
                .coerceAtMost((PROMPT_COOLDOWN / 20).toInt().toLong())
            if (config.visibleAiWarnings) {
                player.sendSystemMessage(
                    Component.literal("Calm down, the Pokémon are processing what you said... $wait s")
                        .withStyle(ChatFormatting.RED)
                )
            }
            return
        }

        lastPromptTicks[player.uuid] = now
        scheduledMessages.clear()

        val ativos = PokemonQuery.findActivePokemon(player)
        val prompt = buildPrompt(player, ativos, "") + "\n\n[the player (owner of the pokemon team) said]: $text"
        File("cobblebrain-ai/comando_ia.txt").writeText(prompt)

        // Marca que esse jogador foi o último a falar
        lastSpeakerPlayer = player
    }

    // NÃO alterar o comportamento de tick/loop do flush
    private fun flushScheduledMessages(server: MinecraftServer) {
        val currentTick = server.tickCount.toLong()
        val ready = scheduledMessages.filter { it.sendAtTick <= currentTick }
        if (ready.isNotEmpty()) {
            ready.forEach { msg ->
                println("=== DEBUG FLUSH ===")
                println("msg.text='${msg.text}'")
                println("msg.speaker='${msg.speaker}'")
                msg.player.sendSystemMessage(Component.literal(msg.text))

                // tenta resolver o falante pelo apelido OU pela espécie
                val ativos = PokemonQuery.findActivePokemon(msg.player)
                ativos.forEach { poke ->
                    println("ativo nickname='${poke.nickname}' especie='${poke.species.resourceIdentifier.path}'")
                }
                val rawName = msg.text.substringBefore(":").trim()

                val speaker = msg.speaker ?: ativos.find { poke ->
                    val nick = poke.nickname?.string // pega o texto puro do Component
                    nick?.equals(rawName, ignoreCase = true) == true ||
                            poke.species.resourceIdentifier.path.equals(rawName, ignoreCase = true)
                }

                println("speaker resolvido = ${speaker?.nickname ?: speaker?.species?.resourceIdentifier?.path ?: "null"}")

                speaker?.let { pokemon ->
                    val entity = pokemon.entity
                    val basePitch = entity?.uuid?.let { pokemonPitchMap[it] } ?: 1.0f
                    expressPokemon(pokemon, basePitch)

                    // bolha de diálogo temporária
                    if (entity != null) {
                        val bubbleText = msg.text.substringAfter(":").trim()
                        spawnSpeechBubble(server, pokemon, bubbleText, 100) // 100 ticks ≈ 5 segundos
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
            scheduledMessages.removeAll(ready)
        }
    }

    // mapa para controlar quando limpar cada bolha
    private val bubbleUntilTick = mutableMapOf<UUID, Long>()
    private val bubbleStands = mutableMapOf<UUID, UUID>()

    fun getBubbleY(entity: Entity): Double {
        // topo da hitbox + pequeno offset
        return entity.eyeY - 1
    }

    fun spawnSpeechBubble(server: MinecraftServer, pokemon: Pokemon, text: String, durationTicks: Int = 60, charsPerTick: Int = 1) {
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

    private fun runSocialTick(server: MinecraftServer) {
        for (player in server.playerList.players) {
            val ativos = PokemonQuery.findActivePokemon(player)
            if (ativos.isEmpty()) continue

            val chance = config.spontaneousDialogueChance

            if (Random.nextDouble() <= chance) {
                scheduledMessages.clear()
                val prompt = buildPrompt(player, ativos, "IMPORTANT: The Pokémons are thinking of something different to say... (use the world variables or refer to something else...)")
                File("cobblebrain-ai/comando_ia.txt").writeText(prompt)
            }
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

        // animações de expressividade
        entity.jumpFromGround()

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

    private fun buildPrompt(player: ServerPlayer, pokemons: List<Pokemon>, moreText: String): String {
        val context = collectWorldContext(player)
        println(context)

        return buildString {
            appendLine(moreText)
            appendLine()
            // Environment
            appendLine("Biome: ${context.biome}")
            appendLine("Weather: ${context.weather}")
            appendLine("Time: ${context.timeOfDay}, ${context.timeLabel})")

            // Location and terrain
            appendLine("Light: ${context.lightLevel}")
            appendLine("Block under the player's feet: ${context.blockUnder}")
            // appendLine("Terrain: ${context.terrainHint}")
            appendLine("Nearby special blocks: ${context.specialBlocks}")

            // Entities
            appendLine("Nearby entities: ${context.nearbyEntities}")
            appendLine("Nearby mobs: ${context.nearbyMobs}")
            appendLine("Items on the ground: ${context.nearbyItems}")

            // Player status
            appendLine("Player health: ${context.health}/${context.maxHealth}")
            // appendLine("Player armor: ${context.armor}")

            // Items in use
            appendLine("Player's main hand: ${context.mainHand}")
            // appendLine("Player's off hand: ${context.offHand}")
            appendLine()

            appendLine("[Active pokemons]")

            pokemons.forEach { p ->
                val allMoves: List<String> = p.moveSet.getMoves().map { it.name }
                appendLine("Nickname: ${p.nickname?.string} | Species: ${p.species.name} | UUID: ${p.uuid} | HP: ${p.currentHealth}/${p.maxHealth} | Lvl: ${p.level} | Nature: ${p.effectiveNature.name} | Moveset: $allMoves | Friendship with player: ${p.friendship} | Fainted: ${p.isFainted()}")
                println("Nickname: ${p.nickname?.string} | Species: ${p.species.name} | UUID: ${p.uuid} | HP: ${p.currentHealth}/${p.maxHealth} | Lvl: ${p.level} | Nature: ${p.effectiveNature.name} | Moveset: $$allMoves | Friendship with player: ${p.friendship} | Fainted: ${p.isFainted()}")
                println(p.types.map { it.name })
                val memories = currentServer?.let { srv ->
                    loadPokemonMemories(
                        srv,
                        p.uuid.toString(),
                        config.maxShortMemory
                    )
                } ?: emptyList()

                if (memories.isNotEmpty()) {
                    appendLine("\nMemories:\n")
                    memories.forEach { m ->
                        appendLine("@Pokemon ${p.nickname?.string ?: p.species.name}: $m\n")
                        println("@Pokemon ${p.nickname?.string ?: p.species.name}: $m\n")
                    }
                }
            }

            appendLine("Important variables:")
            appendLine("AFFECT_FRIENDSHIP_PLUS: ${config.increaseFriendship}")
            appendLine("AFFECT_FRIENDSHIP_MINUS: ${config.decreaseFriendship}")
            appendLine("""##OUTPUT FORMAT##
    - Use pipes (|) then the name of the pokemon to separate the lines of dialogue for each Pokémon.
    Example: 
    PokemonA: ...|PokemonB: ...|PokemonD: ...|PokemonA: ...
    - If only AFFECT_FRIENDSHIP_PLUS = true, you must increase the friendship with the minimum value being 0... 
    - If only AFFECT_FRIENDSHIP_MINUS = true, you must decrease the friendship with the minimum value being 0... 
    - If both are True, you must decide whether to increase or decrease the friendship, depending on the positive or negative impact the Pokémon had on the speech/action (max 5, min -5)
    Example:
    Friendship Pokemon A: 50 + 1 
    Friendship Pokemon B: 50 + -2
    - Then output the memory lines in the format:
      example with short memory: @Pokemon A: <short memory sentence>
      example with long memory: @@Pokemon A: <long memory sentence>
    - Each Pokémon evaluates events from its own perspective.
    - The same event may be recorded differently by different Pokémon: for one it may be a short-term memory (@), while for another it may be a long-term memory (@@), depending on the personal impact.
    - Use a single '@' for short-term memories. Short-term memories represent fleeting perceptions, temporary conditions, or minor occurrences that are relevant in the moment but do not significantly alter identity or history.
    - Use a double '@@' for long-term memories. Long-term memories represent impactful events, defining traits, or meaningful experiences that leave a lasting mark on the Pokémon’s personality, relationships, or sense of self — things that significantly alter identity or history.
    - Pokémon never speak their memories aloud; they are stored after dialogue.
    
    - Finally, at the very end, output one action validation line for each Pokémon, always using exactly one of:
        #PokemonName: attack
        #PokemonName: eat
        #PokemonName: buff
        #PokemonName: debuff enemy
        #PokemonName: sit
        #PokemonName: protect
        #PokemonName: idle
        (if pokemon has primary fire type) #PokemonName: cook 
        (if pokemon has primary steel type) #PokemonName: repair
        (if pokemon has primary grass type) #PokemonName: grow
        (if pokemon has primary ghost type) #PokemonName: shift
    - Every Pokémon must provide exactly one action, If irrelevant, use 'idle'. avoid repeating the same action multiple times...
    - Pokémon with a Friendship level closer to 225 are more likely to follow the player's commands if requested, while those closer to 0 are less likely to be followed and are more prone to following riskier commands on their own (such as attack and protect), but be careful not to overdo it...
    - Pokémon must continue their own emotional thread when asked about it.  
    If the player asks a question, respond from the Pokémon’s perspective, not as if the player is confused.  
    Dialogue must feel like an ongoing conversation, not isolated lines, use the memories to understand the context.

    
    - ALWAYS FOLLOW THE OUTPUTFORMAT WHEN SENDING YOUR RESPONSE, NO HYPHENS
    - USE THE POKEMON NICKNAME OR THE SPECIES IF THE NICKNAME DOES NOT EXIST, NEVER COMBINE THE TWO IN THE MESSAGE...
    - IF A POKEMON'S FRIENDSHIP IS NOT AFFECTED, PUT +0, BUT PUT ALL POKEMONS THAT PARTICIPATED IN THE DIALOGUE IN THE FRIENDSHIP CHANGE
    - SEND YOUR ENTIRE response in ${config.selectedLanguage}""")
        }.trim()
    }

    private var lastResponseContent: String? = null

    private fun checkIaResponse(server: MinecraftServer) {
        val file = File("cobblebrain-ai/resposta_ia.txt")
        if (!file.exists()) return

        val content = file.readText().trim()
        if (content.isEmpty() || content == lastResponseContent) return

        // Linhas para chat (falas + friendship), excluindo memórias
        // 1. Separa falas (chat) e memórias (não vão pro chat)
        val falas = content.split("|")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot { it.startsWith("@") }

        val memoryLines = content.split("|")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filter { it.startsWith("@") }

        println(">>> Fala pega: $memoryLines")

        // 2. Salva as memórias no JSON
        memoryLines.forEach { line ->
            println(">>> Fala pega: $line")
            savePokemonMemory(server, line, config.maxShortMemory)
        }

        //detects if there is any action (#) in the dialogue
        val commandLines = falas.filter { it.startsWith("#") }
        commandLines.forEach { line ->
            val cmd = parseCommand(line)
            if (cmd != null && lastSpeakerPlayer != null) {
                val level = lastSpeakerPlayer!!.level() as ServerLevel
                val pokemon = level.getEntitiesOfClass(Mob::class.java, lastSpeakerPlayer!!.boundingBox.inflate(64.0)) {
                    it.displayName?.string.equals(cmd.pokemonName, ignoreCase = true)
                }.firstOrNull()

                if (pokemon != null) {
                    CommandState.activeCommands[pokemon.uuid] = cmd.action
                }
            }
        }

        fun findPokemonByName(name: String, pokemons: List<Pokemon>): Pokemon? {
            return pokemons.firstOrNull { it.species.name.equals(name, ignoreCase = true) }
        }

        // estrutura para guardar nome + UUID
        data class Participante(val nome: String, val id: String)

        val participantes = mutableListOf<Participante>()

        // adiciona o jogador que falou por último
        lastSpeakerPlayer?.let {
            participantes.add(Participante(it.gameProfile.name, it.uuid.toString()))
        }

        val ativos = server.playerList.players.flatMap { PokemonQuery.findActivePokemon(it) }

        val regex = Regex("""Friendship\s+([\w\s.'♀♂-]+):\s*([\d.,]+)\s*\+\s*(-?\d+)""")

        val matches = regex.findAll(content)
        for (match in matches) {
            val nomePokemon = match.groupValues[1]
            val atual = match.groupValues[2].toDouble()
            val incremento = match.groupValues[3].toDouble()

            println("Pokémon: $nomePokemon")
            println("New friendship: ${atual + incremento}")

            val alvo = ativos.firstOrNull { ativo ->
                val nomeNormalizado = nomePokemon.trim()
                val nickname = ativo.nickname?.string
                nickname?.equals(nomeNormalizado, ignoreCase = true)
                    ?: ativo.species.name.equals(nomeNormalizado, ignoreCase = true)
            }

            if (alvo != null) {
                val incrementoDouble = incremento
                val incrementoInt = incrementoDouble.toInt()

                // só mexe se houver entidade associada
                alvo.entity?.let {
                    if (incrementoDouble > 0 && config.increaseFriendship) {
                        alvo.incrementFriendship(incrementoInt)
                        println("Friendship of ${alvo.species.name} increased to ${alvo.friendship}")

                        val basePitch = (1.0f + incrementoInt * 0.04f).coerceAtMost(1.5f)
                        alvo.entity?.uuid?.let { pokemonPitchMap[it] = basePitch }

                    }

                    if (incrementoDouble < 0 && config.decreaseFriendship) {
                        alvo.decrementFriendship(incrementoInt)
                        println("Friendship of ${alvo.species.name} decreased to ${alvo.friendship}")

                        val basePitch = (1.0f + incrementoInt * 0.04f).coerceAtLeast(0.5f)
                        alvo.entity?.uuid?.let { pokemonPitchMap[it] = basePitch }

                    }
                }
            } else {
                println("I couldn't find $nomePokemon among the active ones.")
            }
        }

            falas.map { it.substringBefore(":").trim() }.distinct().forEach { nome ->
            val poke = findPokemonByName(nome, ativos)
            if (poke != null) {
                participantes.add(Participante(poke.species.name, poke.uuid.toString()))
            } else {
                server.playerList.players
                    .firstOrNull { it.name.string.equals(nome, ignoreCase = true) }
                    ?.let { p ->
                        participantes.add(Participante(p.gameProfile.name, p.uuid.toString()))
                    }
            }
        }

        lastResponseContent = content
        file.writeText("")


    val startTick = server.tickCount.toLong()
        falas.forEachIndexed { i, line ->
            val speakerName = line.substringBefore(":").trim()
            val speaker = findPokemonByName(speakerName, ativos)

            server.playerList.players.forEach { player ->
                scheduledMessages.add(
                    ScheduledMessage(
                        player = player,
                        text = line,
                        sendAtTick = startTick + (i * 100),
                        speaker = speaker
                    )
                )
            }
        }

        lastResponseContent = content
        file.writeText("")
    }
}