package vito.cobblebrain.social

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
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvent
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundSource
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.InteractionHand
import vito.cobblebrain.config.CobblebrainConfig
import vito.cobblebrain.currentServer
import vito.cobblebrain.sensors.collectWorldContext

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

            server.execute {
                scheduledMessages.clear()
                val ativos = battle.activePokemon.mapNotNull { it.battlePokemon }

                if (ativos.size < 2) {
                    println("Batalha iniciada, mas ainda sem Pokémon ativos detectados.")
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
                val playerName = player.name.string
                val prompt = buildPrompt(player, pokemonsTime, "IMPORTANT: \"$playerName entrou em batalha com [$meusNomes] contra [$inimigosNomes]\"")
                File("cobblebrain-ai/comando_ia.txt").writeText(prompt)
                println("$playerName entrou em batalha com [$meusNomes] contra [$inimigosNomes]")
            }
        }

        CobblemonEvents.POKEMON_SENT_POST.subscribe { event: PokemonSentPostEvent ->
            val pokemon = event.pokemon
            val ownerId = pokemon.getOwnerUUID() ?: return@subscribe

            // verifica se o dono está em alguma batalha ativa
            val battle = BattleRegistry.getBattleByParticipatingPlayerId(ownerId)
            if (battle != null) {
                scheduledMessages.clear()
                val species = pokemon.species.name
                val level = pokemon.level
                val ownerName = pokemon.getOwnerPlayer()?.name ?: "Desconhecido"

                // pega o ServerPlayer diretamente
                val player = pokemon.getOwnerPlayer() ?: return@subscribe
                val ativos = PokemonQuery.findActivePokemon(player)

                println("[$ownerName] enviou $species (Lv.$level) para a batalha!")
                val prompt = buildPrompt(player, ativos, "IMPORTANT: No meio da batalha, [$ownerName] enviou $species (Lv.$level) para lutar!")
                File("cobblebrain-ai/comando_ia.txt").writeText(prompt)
            }
        }

        CobblemonEvents.BATTLE_FLED.subscribe { event: BattleFledEvent ->
            val actor = event.player // PlayerBattleActor que fugiu
            val uuids = actor.getPlayerUUIDs()

            if (uuids.isEmpty()) {
                println("${actor.getName().string} fugiu da batalha (não é jogador humano).")
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

        CobblemonEvents.BATTLE_VICTORY.subscribe { event: BattleVictoryEvent ->
            val battle = event.battle

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

        ServerLivingEntityEvents.AFTER_DAMAGE.register { entity, source, amount, newHealth, absorbed ->
            when (entity) {
                is ServerPlayer -> {
                    val now = System.currentTimeMillis()
                    val last = lastPrompt[entity.uuid] ?: 0L
                    if (now - last >= 8500) {
                        scheduledMessages.clear()
                        lastPrompt[entity.uuid] = now
                        val cause = source.msgId
                        println("IMPORTANT: The player took $amount of damage from $cause. Final health: $newHealth")
                        val ativos = PokemonQuery.findActivePokemon(entity)
                        val prompt = buildPrompt(entity, ativos, "IMPORTANT: The player took $amount of damage from $cause. Final health: $newHealth")
                        File("cobblebrain-ai/comando_ia.txt").writeText(prompt)
                    }
                }
                is PokemonEntity -> {
                    val ownerUuid = entity.pokemon.getOwnerUUID()
                    if (ownerUuid != null) {
                        val server = entity.server ?: return@register
                        val owner: ServerPlayer? = server.playerList.getPlayer(ownerUuid)

                        if (owner != null) {
                            val now = System.currentTimeMillis()
                            val last = lastPrompt[owner.uuid] ?: 0L
                            if (now - last >= 8500) {
                                scheduledMessages.clear()
                                lastPrompt[owner.uuid] = now
                                val cause = source.msgId
                                val pokemonName = entity.pokemon.species.name
                                println("Pokémon $pokemonName owned by player ${owner.scoreboardName} took $amount of damage from $cause. Final health: $newHealth")
                                server.playerList.getPlayer(ownerUuid)?.let { owner ->
                                    val ativos = PokemonQuery.findActivePokemon(owner)
                                    val prompt = buildPrompt(owner, ativos, "Pokémon $pokemonName owned by player ${owner.scoreboardName} took $amount of damage from $cause. Final health: $newHealth")
                                    File("cobblebrain-ai/comando_ia.txt").writeText(prompt)
                                }
                            }
                        }
                    }
                }
            }
        }

        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            val prevDialogues = DialogueStore.getDialogues(server)
            dialogueHistory.clear()
            dialogueHistory.addAll(prevDialogues)
        }

        ServerTickEvents.END_SERVER_TICK.register { server ->
            flushScheduledMessages(server)

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

        addDialogue(listOf("${player.name.string}: $text"))

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

                speaker?.let {
                    expressPokemon(it)

                    // define foco social e espectadores
                    currentSpeaker = it
                    speakerUntilTick = server.tickCount.toLong() + 100 // ~5s
                    currentViewers.clear()

                    // espectadores: pokémon ativos do mesmo player, exceto o falante
                    ativos.filter { other -> other != it }.forEach { other ->
                        currentViewers.add(other)
                        val otherEntity = other.entity
                        val speakerEntity = it.entity
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
                val prompt = buildPrompt(player, ativos, "") // <-- ajustar aqui
                File("cobblebrain-ai/comando_ia.txt").writeText(prompt)
            }
        }
    }

    fun playPokemonCry(pokemon: Pokemon) {
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
            1.0f
        )
    }

    fun expressPokemon(pokemon: Pokemon) {
        val entity = pokemon.entity ?: return
        println(pokemon)

        // sempre toca o cry
        playPokemonCry(pokemon)

        // fallback de "expressividade"
        entity.jumpFromGround()                       // dá um pulinho
        entity.animateHurt(1.0f)                  // piscada de dano (efeito visual)
        entity.swing(InteractionHand.MAIN_HAND)       // se tiver braço, faz swing
    }
    private val MAX_DIALOGUES = config.maxDialogueSaves

    // Histórico de diálogos (cada item = lista de falas)
    private val dialogueHistory = ArrayDeque<List<String>>()

    private fun addDialogue(dialogue: List<String>) {
        if (dialogueHistory.size >= MAX_DIALOGUES) {
            dialogueHistory.removeFirst() // descarta o mais antigo
        }
        dialogueHistory.addLast(dialogue)
    }

    private fun buildPrompt(player: ServerPlayer, pokemons: List<Pokemon>, moreText: String): String {
        val context = collectWorldContext(player)

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
            appendLine("Terrain: ${context.terrainHint}")
            appendLine("Nearby special blocks: ${context.specialBlocks}")

            // Entities
            appendLine("Nearby entities: ${context.nearbyEntities}")
            appendLine("Nearby mobs: ${context.nearbyMobs}")
            appendLine("Items on the ground: ${context.nearbyItems}")

            // Player status
            appendLine("Player health: ${context.health}/${context.maxHealth}")
            appendLine("Player armor: ${context.armor}")

            // Items in use
            appendLine("Player's main hand: ${context.mainHand}")
            appendLine("Player's off hand: ${context.offHand}")
            appendLine()

            // no momento o historico de dialogo esta muito pesado pra processar mais q 2 dialogos no prompt...
            appendLine("[Recent dialogue history]")
            dialogueHistory.forEachIndexed { i, dialogo ->
                appendLine("Dialogue ${i+1}:")
                dialogo.forEach { fala -> appendLine(fala) }
                appendLine()
            }

            appendLine("[Active pokemons]")

            pokemons.forEach { p ->
                val allMoves: List<String> = p.moveSet.getMoves().map { it.name }
                appendLine("Nickname: ${p.nickname?.string} | Species: ${p.species.name} | UUID: ${p.uuid} | HP: ${p.currentHealth}/${p.maxHealth} | Lvl: ${p.level} | Nature: ${p.effectiveNature.name} | Moveset: $allMoves | Friendship with player: ${p.friendship} | Fainted: ${p.isFainted()}")
                println("Nickname: ${p.nickname?.string} | Species: ${p.species.name} | UUID: ${p.uuid} | HP: ${p.currentHealth}/${p.maxHealth} | Lvl: ${p.level} | Nature: ${p.effectiveNature.name} | Moveset: $$allMoves | Friendship with player: ${p.friendship} | Fainted: ${p.isFainted()}")
            }
            // ADIÇÃO: histórico de interações
            appendLine()
            appendLine("[Interaction history]")

            val playerName = player.gameProfile.name
            val playerId = player.uuid.toString()

            // Interações entre pokémons do time
            for (i in pokemons.indices) {
                for (j in i + 1 until pokemons.size) {
                    val p1 = pokemons[i]
                    val p2 = pokemons[j]

                    val count = InteractionStore.getInteractionCount(
                        player.server,
                        p1.species.name, p1.uuid.toString(),
                        p2.species.name, p2.uuid.toString()
                    )

                    if (count > 0) {
                        appendLine("${p1.nickname?.string} (${p1.species.name}) - ${p2.nickname?.string} (${p2.species.name}): $count interactions")
                    }
                }
            }

            // Interações entre cada pokémon e o jogador
            pokemons.forEach { p ->
                val count = InteractionStore.getInteractionCount(
                    player.server,
                    p.species.name, p.uuid.toString(),
                    playerName, playerId
                )

                if (count > 0) {
                    appendLine("${p.nickname?.string} (${p.species.name}) - Player(${playerName}): $count interactions")
                }
            }
            appendLine("Important variables:")
            appendLine("AFFECT_FRIDENSHIP: ${config.dialogueAffectFriendship}")
            appendLine("""##OUTPUT FORMAT##
    PokemonA: ...|PokemonB: ...|PokemonD: ...| 
    if AFFECT_FRIENDSHIP in the user prompt is true, include:
    Friendship Pokemon A: 50 + 1 
    Friendship Pokemon B: 50 + -2
    ...
    
    - ALWAYS FOLLOW THE OUTPUTFORMAT WHEN SENDING YOUR RESPONSE, NO HYPHENS
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

        val falas = content.split("|")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        // se já existe um diálogo iniciado pelo jogador, completa ele
        if (dialogueHistory.isNotEmpty()) {
            val ultimo = dialogueHistory.removeLast().toMutableList()
            ultimo.addAll(falas)
            addDialogue(ultimo)
            DialogueStore.addDialogue(server, ultimo)
        } else {
            // fallback: se não tinha diálogo iniciado, cria um novo só com as falas da IA
            addDialogue(falas)
            DialogueStore.addDialogue(server, falas)
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

        val regex = Regex("""Friendship\s+([\w\s.'♀♂-]+):\s*([\d.,]+)\s*\+\s*([\d.,]+)""")
        val match = regex.find(content)

        if (config.dialogueAffectFriendship)
            if (match != null) {
                val nomePokemon = match.groupValues[1]   // "Charmander"
                val atual = match.groupValues[2].toDouble()
                val incremento = match.groupValues[3].toDouble()

                println("Pokémon: $nomePokemon")
                println("New friendship: ${atual + incremento}")

                // procura entre os ativos
                val alvo = ativos.firstOrNull { ativo ->
                    val nomeNormalizado = nomePokemon.trim()
                    val nickname = ativo.nickname?.string

                    nickname?.equals(nomeNormalizado, ignoreCase = true) ?: ativo.species.name.equals(nomeNormalizado, ignoreCase = true)
                }

                if (alvo != null) {
                    alvo.incrementFriendship(incremento.toInt())
                    println("Friendship of ${alvo.species.name} updated to ${alvo.friendship}")
                } else {
                    println("I couldn't find $nomePokemon among the active ones.")
                }
            } else {
                println("I couldn't find the friendship pattern in the response.")
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

        // salva interações no JSON (nome + UUID)
        for (i in participantes.indices) {
            for (j in i + 1 until participantes.size) {
                val qtd = InteractionStore.addInteraction(
                    server,
                    participantes[i].nome, participantes[i].id,
                    participantes[j].nome, participantes[j].id
                )
                println("DEBUG: ${participantes[i].nome} - ${participantes[j].nome} já interagiram $qtd vezes")
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