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
                val prompt = buildPrompt(player, pokemonsTime, "IMPORTANTE: \"$playerName entrou em batalha com [$meusNomes] contra [$inimigosNomes]\"")
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
                val prompt = buildPrompt(player, ativos, "IMPORTANTE: No meio da batalha, [$ownerName] enviou $species (Lv.$level) para lutar!")
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
                        "IMPORTANTE: ${player.name.string} fugiu da batalha!"
                    )
                    File("cobblebrain-ai/comando_ia.txt").writeText(prompt)
                    println("${player.name.string} fugiu da batalha!")
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
                    println("Jogador ${player.scoreboardName} venceu a batalha!")
                    val ativos = PokemonQuery.findActivePokemon(player)
                    val prompt = buildPrompt(player, ativos, "IMPORTANTE: Jogador ${player.scoreboardName} venceu a batalha!")
                    File("cobblebrain-ai/comando_ia.txt").writeText(prompt)
                } else {
                    scheduledMessages.clear()
                    val ativos = PokemonQuery.findActivePokemon(player)
                    val prompt = buildPrompt(player, ativos, "IMPORTANTE: Jogador ${player.scoreboardName} perdeu a batalha!")
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
                        println("IMPORTANTE: O Jogador levou $amount de dano por $cause. Vida final: $newHealth")
                        val ativos = PokemonQuery.findActivePokemon(entity)
                        val prompt = buildPrompt(entity, ativos, "IMPORTANTE: O Jogador levou $amount de dano por $cause. Vida final: $newHealth")
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
                                println("Pokémon $pokemonName do jogador ${owner.scoreboardName} levou $amount de dano por $cause. Vida final: $newHealth")
                                server.playerList.getPlayer(ownerUuid)?.let { owner ->
                                    val ativos = PokemonQuery.findActivePokemon(owner)
                                    val prompt = buildPrompt(owner, ativos, "IMPORTANTE: $pokemonName do jogador levou $amount de dano por $cause. Vida final: $newHealth")
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
            println("[CobbleBrain] IA não está rodando, reiniciando...")
            chatThread = Thread {
                try {
                    val chave = config.apiKey
                    if (chave.isBlank()) {
                        println("[CobbleBrain] Nenhuma chave configurada! Edite o arquivo config/cobblebrain.key")
                        val msg = Component.literal("§c[CobbleBrain] Nenhuma chave de API configurada! Edite config/cobblebrain.key")
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

    private const val PROMPT_COOLDOWN = 50L // ~2.5 segundos (40 ticks)

    val lastPromptTicks = mutableMapOf<UUID, Long>()

    private var lastSpeakerPlayer: ServerPlayer? = null

    fun onPlayerChat(player: ServerPlayer, text: String) {
        val now = player.server.tickCount.toLong()
        val last = lastPromptTicks[player.uuid] ?: 0L
        val diff = now - last

        if (diff < PROMPT_COOLDOWN) {
            val wait = ((PROMPT_COOLDOWN - diff) / 20)
                .coerceAtMost((PROMPT_COOLDOWN / 20).toInt().toLong())
            player.sendSystemMessage(
                Component.literal("Calma, os pokémons estão processando o que você disse... $wait s")
                    .withStyle(ChatFormatting.RED)
            )
            return
        }

        lastPromptTicks[player.uuid] = now
        scheduledMessages.clear()

        val ativos = PokemonQuery.findActivePokemon(player)
        val prompt = buildPrompt(player, ativos, "") + "\n\n[O Jogador disse]: $text"
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
                msg.player.sendSystemMessage(Component.literal(msg.text))
                msg.speaker?.let { speaker ->
                    expressPokemon(speaker)

                    // define foco social e espectadores sem mudar o fluxo original
                    currentSpeaker = speaker
                    speakerUntilTick = server.tickCount.toLong() + 100 // ~5s (100 ticks)
                    currentViewers.clear()

                    // espectadores: pokémon ativos do mesmo player, exceto o falante
                    val ativos = PokemonQuery.findActivePokemon(msg.player)
                    ativos.filter { it != speaker }.forEach { other ->
                        currentViewers.add(other)
                        // aplica imediatamente neste tick para efeito instantâneo
                        val otherEntity = other.entity
                        val speakerEntity = speaker.entity
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

            val chance = when (ativos.size) {
                1 -> 0.15
                2 -> 0.20
                3 -> 0.25
                else -> 0.30
            }

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
            // Ambiente
            appendLine("Bioma: ${context.biome}")
            appendLine("Clima: ${context.weather}")
            appendLine("Horário: ${context.timeOfDay}, ${context.timeLabel})")

            // Localização e terreno
            appendLine("Luz: ${context.lightLevel}")
            appendLine("Bloco sob os pés do jogador: ${context.blockUnder}")
            appendLine("Terreno: ${context.terrainHint}")
            appendLine("Blocos especiais próximos: ${context.specialBlocks}")

            // Entidades
            appendLine("Entidades próximas: ${context.nearbyEntities}")
            appendLine("Mobs próximos: ${context.nearbyMobs}")
            appendLine("Itens no chão: ${context.nearbyItems}")

            // Status do jogador
            appendLine("Vida do jogador: ${context.health}/${context.maxHealth}")
            appendLine("Armadura do jogador: ${context.armor}")

            // Itens em uso
            appendLine("Mão principal do jogador: ${context.mainHand}")
            appendLine("Mão secundária do jogador: ${context.offHand}")
            appendLine()

            // no momento o historico de dialogo esta muito pesado pra processar mais q 2 dialogos no prompt...
            appendLine("[Histórico de diálogos recentes]")
            dialogueHistory.forEachIndexed { i, dialogo ->
                appendLine("Diálogo ${i+1}:")
                dialogo.forEach { fala -> appendLine(fala) }
                appendLine()
            }

            appendLine("[Pokemons ativos]")

            pokemons.forEach { p ->
                appendLine("Especie: ${p.species.name} | Apelido: ${p.nickname} | HP: ${p.currentHealth}/${p.maxHealth} | Lvl: ${p.level} | Natureza: ${p.effectiveNature.name} | Moveset: ${p.moveSet} | Amizade com player: ${p.friendship} | Desmaiado: ${p.isFainted()}")
            }
            // ADIÇÃO: histórico de interações
            appendLine()
            appendLine("[Histórico de interações]")

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
                        appendLine("${p1.species.name} - ${p2.species.name}: $count interações")
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
                    appendLine("${p.species.name} - Jogador(${playerName}): $count interações")
                }
            }
            appendLine("Sempre mande sua resposta na língua ${config.selectedLanguage}")
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

        val regex = Regex("""[: ]\s*([\w\s.'♀♂-]+)\s*:\s*([\d.,]+)\s*\+\s*([\d.,]+)""")
        val match = regex.find(content)

        if (config.dialogueAffectFriendship)
            if (match != null) {
                val nomePokemon = match.groupValues[1]   // "Charmander"
                val atual = match.groupValues[2].toDouble()
                val incremento = match.groupValues[3].toDouble()
                val novoValor = atual + incremento

                println("Pokémon: $nomePokemon")
                println("Amizade nova: $novoValor")

                // procura entre os ativos
                val alvo = ativos.firstOrNull { it.species.name.equals(nomePokemon, ignoreCase = true) }

                if (alvo != null) {
                    alvo.setFriendship(novoValor.toInt())
                    println("Amizade de ${alvo.species.name} atualizada para ${novoValor.toInt()}")
                } else {
                    println("Não encontrei $nomePokemon entre os ativos.")
                }
            } else {
                println("Não consegui encontrar o padrão de amizade na resposta.")
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