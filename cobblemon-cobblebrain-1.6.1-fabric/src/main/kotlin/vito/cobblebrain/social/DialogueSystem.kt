package vito.cobblebrain.social

import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.battles.BattleFledEvent
import com.cobblemon.mod.common.api.events.battles.BattleStartedEvent
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent
import com.cobblemon.mod.common.api.events.pokemon.PokemonSentEvent
import com.cobblemon.mod.common.battles.BattleRegistry
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import kotlin.random.Random
import com.cobblemon.mod.common.pokemon.Pokemon
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
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
import vito.cobblebrain.client.CobblebrainClientHandler
import vito.cobblebrain.config.ConfigHandler.config
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

object DialogueSystem {
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
                    .append(Component.literal("/mpk <message>").withStyle(ChatFormatting.AQUA))
                    .append(" to talk to Pokemón.")
            )

            // Lembrete sobre config
            player.sendSystemMessage(
                Component.literal("customize the mod (and its language) as you wish in ")
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal("config/cobblebrain.json5").withStyle(ChatFormatting.AQUA))
            )
        }

        ServerMessageEvents.CHAT_MESSAGE.register { message, sender, _ ->
            if (!config.listenToChat) return@register  // checa em tempo real

            val rawContent = message.signedContent()

            if (config.onlyNearbyChat) {
                val nearbyPlayers = sender.server.playerList.players.filter { other ->
                    other.distanceTo(sender) <= 15.0
                }

                if (nearbyPlayers.isEmpty()) {
                    sender.sendSystemMessage(Component.literal("Nenhum jogador próximo para ouvir sua fala."))
                    return@register
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

    CobblemonEvents.BATTLE_STARTED_POST.subscribe { event: BattleStartedEvent ->
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
                ServerPlayNetworking.send(player, CobblebrainClientHandler.PromptPayload(prompt))
                println("IMPORTANT: I started a battle with my team[$meusNomes] against [$inimigosNomes]")
            }
        }
    }

        CobblemonEvents.POKEMON_SENT_POST.subscribe { event: PokemonSentEvent ->
            val pokemon = event.pokemon
            val ownerId = pokemon.getOwnerUUID() ?: return@subscribe

            val battle = BattleRegistry.getBattleByParticipatingPlayerId(ownerId)
            if (battle != null && config.dialogueOnBattle) {
                scheduledMessages.clear()

                val nickname = pokemon.nickname
                val species = pokemon.species.name
                val level = pokemon.level
                val ownerPlayer = pokemon.getOwnerPlayer() ?: return@subscribe

                val displayName = if (nickname != null) species else nickname ?: species
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
                ServerPlayNetworking.send(ownerPlayer, CobblebrainClientHandler.PromptPayload(prompt))
            }
        }

        CobblemonEvents.BATTLE_FLED.subscribe { event: BattleFledEvent ->
            val actor = event.player
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
                        ServerPlayNetworking.send(player, CobblebrainClientHandler.PromptPayload(prompt))
                        println("${player.name.string} we run away from the battle")
                    }
                }
            }
        }

        CobblemonEvents.BATTLE_VICTORY.subscribe { event: BattleVictoryEvent ->
            val battle = event.battle
            if (config.dialogueOnBattle) {
                for (player in battle.players) {
                    val myActor = battle.actors.firstOrNull { it.uuid == player.uuid } ?: continue

                    if (event.winners.contains(myActor)) {
                        scheduledMessages.clear()
                        println("We won the battle")
                        val ativos = PokemonQuery.findActivePokemon(player)
                        val prompt = buildPrompt(player, ativos, "IMPORTANT: We won the battle")
                        ServerPlayNetworking.send(player, CobblebrainClientHandler.PromptPayload(prompt))
                    } else {
                        scheduledMessages.clear()
                        val ativos = PokemonQuery.findActivePokemon(player)
                        val prompt = buildPrompt(player, ativos, "IMPORTANT: We lost the battle")
                        ServerPlayNetworking.send(player, CobblebrainClientHandler.PromptPayload(prompt))
                    }
                }
            }
        }

        ServerLivingEntityEvents.AFTER_DAMAGE.register { entity, source, amount, newHealth, absorbed ->
            when (entity) {
                is ServerPlayer -> {
                    val ativos = PokemonQuery.findActivePokemon(entity)
                    if (ativos.isEmpty()) return@register

                    val now = System.currentTimeMillis()
                    val last = lastPrompt[entity.uuid] ?: 0L
                    if (now - last >= 22000 && config.dialogueOnDamage) {
                        scheduledMessages.clear()
                        lastPrompt[entity.uuid] = now
                        val cause = source.msgId
                        println("IMPORTANT: I took $amount of damage from $cause. Final health: $newHealth")
                        val prompt = buildPrompt(
                            entity,
                            ativos,
                            "IMPORTANT: I took $amount of damage from $cause. Final health: $newHealth"
                        )
                        ServerPlayNetworking.send(entity, CobblebrainClientHandler.PromptPayload(prompt))
                    }
                }

                is PokemonEntity -> {
                    val ownerUuid = entity.pokemon.getOwnerUUID()
                    if (ownerUuid != null && config.dialogueOnDamage) {
                        val server = entity.server ?: return@register
                        val owner: ServerPlayer? = server.playerList.getPlayer(ownerUuid)

                        if (owner != null) {
                            val ativos = PokemonQuery.findActivePokemon(owner)
                            if (ativos.isEmpty()) return@register

                            val now = System.currentTimeMillis()
                            val last = lastPrompt[owner.uuid] ?: 0L
                            if (now - last >= 22000) {
                                scheduledMessages.clear()
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
                                ServerPlayNetworking.send(owner, CobblebrainClientHandler.PromptPayload(prompt))
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

            //if (server.tickCount % 70 == 0) {
                //checkIaResponse(server)
            //}

            //if (server.tickCount % 20 == 0) {
            //ensureChatRunning()
            //}
        }
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

    var lastSpeakerPlayer: ServerPlayer? = null

    fun onPlayerChat(player: ServerPlayer, text: String) {
        scheduledMessages.clear()
        val ativos = PokemonQuery.findActivePokemon(player)
        val prompt = buildPrompt(player, ativos, "\n\n$text")

        // envia prompt para o cliente processar
        ServerPlayNetworking.send(player, CobblebrainClientHandler.PromptPayload(prompt))

        lastSpeakerPlayer = player
    }


    // NÃO alterar o comportamento de tick/loop do flush
    private fun flushScheduledMessages(server: MinecraftServer) {
        val currentTick = server.tickCount.toLong()
        val ready = scheduledMessages.filter { it.sendAtTick <= currentTick }
        if (ready.isNotEmpty()) {
            ready.forEach { msg ->
                if (msg.text.startsWith("#") ||
                    (!config.showFriendship && msg.text.lowercase().startsWith("friendship"))
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

                    val component = if (regex.containsMatchIn(text)) {
                        // Mensagem inteira em vermelho
                        Component.literal(text).withStyle { style ->
                            style.withColor(ChatFormatting.RED)
                        }
                    } else {
                        // Mensagem normal
                        Component.literal(text)
                    }

                    msg.player.sendSystemMessage(component)
                }

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
                    if (entity != null && config.chatbubbles) {
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
                val prompt = buildPrompt(
                    player,
                    ativos,
                    "IMPORTANT: The Pokemon are thinking of something different to say... (use the world variables or refer to something else...)"
                )

                // envia prompt direto para o cliente processar
                ServerPlayNetworking.send(player, CobblebrainClientHandler.PromptPayload(prompt))

                println("Spontaneous dialogue attempt triggered")
                return
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
        //println(context)

        return buildString {
            appendLine(moreText)
            appendLine()
            // Environment
            appendLine("Biome: ${context.biome}")
            appendLine("Weather: ${context.weather}")
            appendLine("Time: ${context.timeOfDay}, ${context.timeLabel})")

            if (!config.lowTokenMode) {
                // Location and terrain
                appendLine("Light: ${context.lightLevel}")
                appendLine("Block under the player's feet: ${context.blockUnder}")
                // appendLine("Terrain: ${context.terrainHint}")
                appendLine("Nearby special blocks: ${context.specialBlocks}")
            }

            // Entities
            if (!config.lowTokenMode) {
                appendLine("Nearby entities: ${context.nearbyEntities}")
                appendLine("Nearby mobs: ${context.nearbyMobs}")
            }
            appendLine("Items on the ground: ${context.nearbyItems}")

            // Player status
            appendLine("Player health: ${context.health}/${context.maxHealth}")
            // appendLine("Player armor: ${context.armor}")

            // Items in use
            appendLine("Player's main hand: ${context.mainHand}")
            // appendLine("Player's offhand: ${context.offHand}")
            appendLine()

            appendLine("[Active pokemons]")

            pokemons.forEach { p ->
                val allMoves: List<String> = p.moveSet.getMoves().map { it.name }
                appendLine("Nickname: ${p.nickname?.string} | Species: ${p.species.name} | UUID: ${p.uuid} | HP: ${p.currentHealth}/${p.maxHealth} | Lvl: ${p.level} | Nature: ${p.effectiveNature.name} | Moveset: $allMoves | Friendship with player: ${p.friendship} | Fainted: ${p.isFainted()} | Is flying: ${p.entity?.isPokemonFlying} | Is player mounted: ${p.entity!!.passengers.any { it is ServerPlayer }}")
                //println("Nickname: ${p.nickname?.string} | Species: ${p.species.name} | UUID: ${p.uuid} | HP: ${p.currentHealth}/${p.maxHealth} | Lvl: ${p.level} | Nature: ${p.effectiveNature.name} | Moveset: $$allMoves | Friendship with player: ${p.friendship} | Fainted: ${p.isFainted()}")
                //println(p.types.map { it.name })
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
                        //println("@Pokemon ${p.nickname?.string ?: p.species.name}: $m\n")
                    }
                }
            }

            appendLine("Important variables:")
            appendLine("AFFECT_FRIENDSHIP_PLUS: ${config.increaseFriendship}")
            appendLine("AFFECT_FRIENDSHIP_MINUS: ${config.decreaseFriendship}")
            appendLine("Send the entire response in ${config.selectedLanguage}")
        }.trim()
    }

    private var lastResponseContent: String? = null

    fun checkIaResponse(server: MinecraftServer, content: String) {
        if (content.isBlank() || content == lastResponseContent) return

        // Linhas para chat (falas + friendship), excluindo memórias
        val falas = content.split("|")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot { it.startsWith("@") }

        val memoryLines = content.split("|")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filter { it.startsWith("@") }

        // 1. Salva as memórias no JSON
        memoryLines.forEach { line ->
            savePokemonMemory(server, line, config.maxShortMemory)
        }

        // 2. Detecta ações (#)
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

        // 3. Friendship updates
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

        // 4. Agenda mensagens para players
        lastResponseContent = content
        val startTick = server.tickCount.toLong()
        falas.forEachIndexed { i, line ->
            val speakerName = line.substringBefore(":").trim()
            val speaker = ativos.firstOrNull { it.species.name.equals(speakerName, ignoreCase = true) }

            server.playerList.players.forEach { player ->
                scheduledMessages.add(
                    ScheduledMessage(
                        player = player,
                        text = line,
                        sendAtTick = if (i == 0) startTick else startTick + (i * 100),
                        speaker = speaker
                    )
                )
            }
        }
    }
}