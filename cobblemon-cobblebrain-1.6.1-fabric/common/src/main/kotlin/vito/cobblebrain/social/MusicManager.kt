package vito.cobblebrain.social

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Mob
import vito.cobblebrain.config.ConfigHandler
import vito.cobblebrain.sensors.CommandState
import java.util.*
import java.util.concurrent.ConcurrentHashMap

data class MusicNote(
    val tick: Int,
    val note: Int,
    val durationTicks: Int,
    val isVocal: Boolean,
    val instrument: String? = null,
    val lyric: String? = null
)

data class MusicBlock(
    val bpm: Int,
    val pokemonName: String,
    val key: String,
    val section: String,
    val status: String,
    val notes: List<MusicNote>,
    val totalTicks: Int,
    val notesSummary: String? = null
)

data class StepNote(
    val step: Int,
    val note: Int,
    val lyric: String? = null,
    val instrument: String? = null,
    val isVocal: Boolean = false
)

class ActivePerformance(
    val pokemonUuid: UUID,
    val ownerUuid: UUID,
    var pokemonName: String,
    var bpm: Int,
    var key: String,
    var currentSection: String,
    val isLongSong: Boolean = false,
    var musicalNotes: String = "",
    val buffer: MutableList<MusicBlock> = mutableListOf(),
    var currentBlockIndex: Int = 0,
    var isPlaying: Boolean = false,
    var blockStartTick: Long = 0L,
    var lastNoteTickExecuted: Int = -1,
    var nextChokeTick: Long = -1L,
    var cryIdToChoke: ResourceLocation? = null,
    var totalBlocksReceived: Int = 0,
    var isFinished: Boolean = false,
    var block1RawText: String = "",
    val drumPatterns: MutableMap<String, List<String>> = mutableMapOf(),
    val melodicPatterns: MutableMap<String, MutableMap<String, MutableList<StepNote>>> = mutableMapOf(),
    val voicePatterns: MutableMap<String, MutableList<StepNote>> = mutableMapOf(),
    val accumulatedNotes: MutableList<MusicNote> = mutableListOf(),
    var totalBars: Int = 0,
    var waitingSinceTick: Long = 0L,
    var customTheme: String = ""
) {
    val maxBlocks: Int get() = if (isLongSong) 2 else 1
}

object MusicManager {
    val activePerformances: MutableMap<UUID, ActivePerformance> = ConcurrentHashMap()

    /**
     * Converts a note number (0 to 24 semitones) to Minecraft pitch (0.5 to 2.0).
     * Note 12 = pitch 1.0 (standard).
     */
    fun noteToPitch(note: Int): Float {
        val clampedNote = note.coerceIn(0, 24)
        return Math.pow(2.0, (clampedNote - 12) / 12.0).toFloat().coerceIn(0.5f, 2.0f)
    }

    /**
     * Maps instrument name to Minecraft vanilla Note Block SoundEvents.
     */
    fun getInstrumentSound(instrument: String): SoundEvent {
        return when (instrument.lowercase().trim()) {
            "bass", "bassline" -> SoundEvents.NOTE_BLOCK_BASS.value()
            "snare" -> SoundEvents.NOTE_BLOCK_SNARE.value()
            "hat", "hihat", "hi-hat" -> SoundEvents.NOTE_BLOCK_HAT.value()
            "basedrum", "kick", "drum", "drums" -> SoundEvents.NOTE_BLOCK_BASEDRUM.value()
            "bell" -> SoundEvents.NOTE_BLOCK_BELL.value()
            "flute" -> SoundEvents.NOTE_BLOCK_FLUTE.value()
            "chime" -> SoundEvents.NOTE_BLOCK_CHIME.value()
            "guitar" -> SoundEvents.NOTE_BLOCK_GUITAR.value()
            "xylophone" -> SoundEvents.NOTE_BLOCK_XYLOPHONE.value()
            "iron_xylophone" -> SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE.value()
            "cow_bell" -> SoundEvents.NOTE_BLOCK_COW_BELL.value()
            "didgeridoo" -> SoundEvents.NOTE_BLOCK_DIDGERIDOO.value()
            "bit" -> SoundEvents.NOTE_BLOCK_BIT.value()
            "banjo" -> SoundEvents.NOTE_BLOCK_BANJO.value()
            "pling" -> SoundEvents.NOTE_BLOCK_PLING.value()
            "harp", "piano", "melody", "lead" -> SoundEvents.NOTE_BLOCK_HARP.value()
            else -> SoundEvents.NOTE_BLOCK_HARP.value()
        }
    }

    fun normalizeInstrumentName(name: String): String {
        val clean = name.removeSuffix("_PATTERNS").removeSuffix("_PATTERN").lowercase().trim()
        return when (clean) {
            "piano", "melody", "lead" -> "harp"
            "kick", "drum", "drums" -> "basedrum"
            "hihat", "hi-hat" -> "hat"
            "bassline" -> "bass"
            else -> clean
        }
    }

    data class TrackerParseResult(
        val block: MusicBlock,
        val drumPatterns: Map<String, List<String>>,
        val melodicPatterns: Map<String, MutableMap<String, MutableList<StepNote>>>,
        val voicePatterns: Map<String, MutableList<StepNote>>,
        val numBars: Int
    )

    /**
     * Parses a BeepBox / Tracker style musical piece based on fixed 16-step patterns and a timeline matrix.
     * Supports inheritance of patterns across multi-block generation and bar offsets.
     */
    fun parseTrackerMusicWithState(
        text: String,
        baseBarOffset: Int = 0,
        inheritedDrumPatterns: Map<String, List<String>> = emptyMap(),
        inheritedMelodicPatterns: Map<String, Map<String, List<StepNote>>> = emptyMap(),
        inheritedVoicePatterns: Map<String, List<StepNote>> = emptyMap(),
        inheritedBpm: Int? = null,
        inheritedKey: String? = null
    ): TrackerParseResult? {
        val normalized = text
            .replace("```text", "")
            .replace("```", "")
            .replace(Regex("""\|+(?=\s*(?:\[|[A-Za-z_]+:|P\d+\s*=))"""), "\n")
            .trim()
        val lines = normalized.lines()

        var currentSection = ""
        val songInfo = mutableMapOf<String, String>()
        val drumPatterns = mutableMapOf<String, List<String>>()
        drumPatterns.putAll(inheritedDrumPatterns)

        val melodicPatterns = mutableMapOf<String, MutableMap<String, MutableList<StepNote>>>()
        for ((inst, map) in inheritedMelodicPatterns) {
            val destMap = melodicPatterns.computeIfAbsent(normalizeInstrumentName(inst)) { mutableMapOf() }
            for ((pName, pNotes) in map) {
                destMap[pName] = pNotes.toMutableList()
            }
        }

        val voicePatterns = mutableMapOf<String, MutableList<StepNote>>()
        for ((pName, pNotes) in inheritedVoicePatterns) {
            voicePatterns[pName] = pNotes.toMutableList()
        }

        val timelineTracks = mutableMapOf<String, List<String>>()

        for (rawLine in lines) {
            val cleanLine = rawLine.substringBefore("//").substringBefore("#").trim()
            if (cleanLine.isBlank()) continue

            if (cleanLine.startsWith("[") && cleanLine.contains("]")) {
                currentSection = cleanLine.substringAfter("[").substringBefore("]").trim().uppercase()
                if (currentSection == "STATE_UPDATES" || currentSection == "UPDATES" || currentSection == "SYSTEM_UPDATES") {
                    break
                }
                continue
            }

            when {
                currentSection == "SONG_INFO" || currentSection == "INFO" -> {
                    val colon = cleanLine.indexOf(':')
                    if (colon != -1) {
                        val key = cleanLine.substring(0, colon).trim().uppercase()
                        val value = cleanLine.substring(colon + 1).trim()
                        songInfo[key] = value
                    }
                }
                currentSection.startsWith("DRUM") -> {
                    // P1 = B . H . S . H . B . B . S . H .
                    val eq = cleanLine.indexOf('=')
                    if (eq != -1) {
                        val patName = cleanLine.substring(0, eq).trim().uppercase()
                        val patBody = cleanLine.substring(eq + 1).trim()
                        val tokens = patBody.split(Regex("[,|\\s]+")).map { it.trim() }.filter { it.isNotBlank() }
                        drumPatterns[patName] = tokens
                    }
                }
                currentSection.startsWith("VOICE") || currentSection.startsWith("VOCAL") || currentSection.startsWith("SING") -> {
                    // P1 = 0:12:Bulba | 4:14:sa | 8:16:saur | 12:19:POWER!
                    val eq = cleanLine.indexOf('=')
                    if (eq != -1) {
                        val patName = cleanLine.substring(0, eq).trim().uppercase()
                        val patBody = cleanLine.substring(eq + 1).trim()
                        val notesList = voicePatterns.computeIfAbsent(patName) { mutableListOf() }
                        notesList.clear()
                        val entries = patBody.split(Regex("[|,\r\n]+")).map { it.trim() }.filter { it.isNotBlank() }
                        for (entry in entries) {
                            val parts = entry.split(":")
                            if (parts.size >= 2) {
                                val step = parts[0].trim().toIntOrNull()?.coerceIn(0, 15) ?: continue
                                val note = parts[1].trim().toIntOrNull()?.coerceIn(0, 24) ?: 12
                                val lyric = parts.drop(2).joinToString(":").trim().takeIf { it.isNotBlank() }
                                notesList.add(StepNote(step, note, lyric = lyric, isVocal = true))
                            }
                        }
                    }
                }
                currentSection == "TIMELINE" -> {
                    // BARS:  1,  2,  3,  4,  5,  6,  7,  8
                    // DRUMS: P1, P1, P1, P2, P1, P1, P1, P2
                    val colon = cleanLine.indexOf(':')
                    if (colon != -1) {
                        val rawTrackName = cleanLine.substring(0, colon).trim()
                        val trackName = normalizeInstrumentName(rawTrackName).uppercase()
                        val trackBody = cleanLine.substring(colon + 1).trim()
                        val items = trackBody.split(Regex("[,|\\s]+")).map { it.trim().uppercase() }.filter { it.isNotBlank() }
                        timelineTracks[trackName] = items
                    }
                }
                else -> {
                    // Instrument pattern, e.g. [BASS_PATTERNS], [HARP_PATTERNS], [BASS], [HARP]
                    val instName = normalizeInstrumentName(currentSection)
                    if (instName.isNotBlank()) {
                        val eq = cleanLine.indexOf('=')
                        if (eq != -1) {
                            val patName = cleanLine.substring(0, eq).trim().uppercase()
                            val patBody = cleanLine.substring(eq + 1).trim()
                            val instMap = melodicPatterns.computeIfAbsent(instName) { mutableMapOf() }
                            val notesList = instMap.computeIfAbsent(patName) { mutableListOf() }
                            notesList.clear()
                            val entries = if (patBody.contains("|") || patBody.contains(",")) {
                                patBody.split(Regex("[|,\r\n]+")).map { it.trim() }.filter { it.isNotBlank() }
                            } else {
                                patBody.split(Regex("\\s+")).map { it.trim() }.filter { it.isNotBlank() }
                            }
                            for (entry in entries) {
                                val parts = entry.split(":")
                                if (parts.size >= 2) {
                                    val step = parts[0].trim().toIntOrNull()?.coerceIn(0, 15) ?: continue
                                    val note = parts[1].trim().toIntOrNull()?.coerceIn(0, 24) ?: 12
                                    notesList.add(StepNote(step, note, instrument = instName, isVocal = false))
                                }
                            }
                        }
                    }
                }
            }
        }

        val bpm = songInfo["TEMPO"]?.toIntOrNull()
            ?: songInfo["BPM"]?.toIntOrNull()
            ?: inheritedBpm
            ?: 120
        val key = songInfo["KEY"]?.ifBlank { inheritedKey ?: "C_MAJOR" } ?: (inheritedKey ?: "C_MAJOR")
        val pokemonName = songInfo["POKEMON"] ?: ""
        val notesSummary = songInfo["NOTES"]

        // Determine max bars across all track lines (ignore BARS / BAR header row)
        val numBars = timelineTracks.filterKeys { it != "BARS" && it != "BAR" }
            .values.maxOfOrNull { it.size } ?: 0
        if (numBars <= 0) return null

        val ticksPerStep = 300.0 / bpm.toDouble().coerceIn(40.0, 240.0)
        val resultNotes = mutableListOf<MusicNote>()

        val voiceDur = Math.round(ticksPerStep * 3).toInt().coerceIn(4, 25)
        val instDur = Math.round(ticksPerStep * 2).toInt().coerceIn(2, 20)

        for (barIndex in 0 until numBars) {
            val actualBarIndex = baseBarOffset + barIndex
            val barStartStep = actualBarIndex * 16

            // 1. Drums
            val drumPatKey = timelineTracks["DRUMS"]?.getOrNull(barIndex)
                ?: timelineTracks["DRUM"]?.getOrNull(barIndex)
                ?: timelineTracks["BASEDRUM"]?.getOrNull(barIndex)
            if (drumPatKey != null && drumPatKey != "--" && drumPatKey != "-" && drumPatKey != "." && drumPatKey != "NONE") {
                val tokens = drumPatterns[drumPatKey]
                if (tokens != null) {
                    for (step in 0 until minOf(tokens.size, 16)) {
                        val token = tokens[step].uppercase()
                        if (token == "." || token == "-" || token == "--" || token == "0") continue

                        val tick = Math.round((barStartStep + step) * ticksPerStep).toInt()
                        if (token.contains("B") || token.contains("K")) {
                            resultNotes.add(MusicNote(tick, 12, 2, isVocal = false, instrument = "basedrum"))
                        }
                        if (token.contains("S")) {
                            resultNotes.add(MusicNote(tick, 12, 2, isVocal = false, instrument = "snare"))
                        }
                        if (token.contains("H")) {
                            resultNotes.add(MusicNote(tick, 12, 2, isVocal = false, instrument = "hat"))
                        }
                        if (token.contains("C")) {
                            resultNotes.add(MusicNote(tick, 18, 2, isVocal = false, instrument = "pling"))
                        }
                    }
                }
            }

            // 2. Voice
            val voicePatKey = timelineTracks["VOICE"]?.getOrNull(barIndex)
                ?: timelineTracks["VOCAL"]?.getOrNull(barIndex)
                ?: timelineTracks["VOCALS"]?.getOrNull(barIndex)
                ?: timelineTracks["SING"]?.getOrNull(barIndex)
            if (voicePatKey != null && voicePatKey != "--" && voicePatKey != "-" && voicePatKey != "." && voicePatKey != "NONE") {
                val vNotes = voicePatterns[voicePatKey]
                if (vNotes != null) {
                    for (sn in vNotes) {
                        val tick = Math.round((barStartStep + sn.step) * ticksPerStep).toInt()
                        resultNotes.add(MusicNote(tick, sn.note, voiceDur, isVocal = true, lyric = sn.lyric))
                    }
                }
            }

            // 3. Melodic Instruments (BASS, HARP, FLUTE, etc.)
            for ((instName, patMap) in melodicPatterns) {
                val trackKey = instName.uppercase()
                val patKey = timelineTracks[trackKey]?.getOrNull(barIndex)
                if (patKey != null && patKey != "--" && patKey != "-" && patKey != "." && patKey != "NONE") {
                    val instNotes = patMap[patKey]
                    if (instNotes != null) {
                        for (sn in instNotes) {
                            val tick = Math.round((barStartStep + sn.step) * ticksPerStep).toInt()
                            resultNotes.add(MusicNote(tick, sn.note, instDur, isVocal = false, instrument = instName))
                        }
                    }
                }
            }
        }

        if (resultNotes.isEmpty()) return null

        val totalTicks = Math.round(numBars * 16 * ticksPerStep).toInt() + 15
        return TrackerParseResult(
            block = MusicBlock(
                bpm = bpm,
                pokemonName = pokemonName,
                key = key,
                section = "TRACKER",
                status = "END",
                notes = resultNotes.sortedBy { it.tick },
                totalTicks = totalTicks,
                notesSummary = notesSummary
            ),
            drumPatterns = drumPatterns,
            melodicPatterns = melodicPatterns,
            voicePatterns = voicePatterns,
            numBars = numBars
        )
    }

    fun parseTrackerMusic(text: String): MusicBlock? = parseTrackerMusicWithState(text)?.block

    /**
     * Parses a compact music chunk in either BeepBox / Tracker format or legacy ~MUSIC format.
     */
    fun parseMusicBlock(text: String): MusicBlock? {
        if (text.contains("[TIMELINE]", ignoreCase = true) ||
            text.contains("[DRUM_PATTERNS]", ignoreCase = true) ||
            text.contains("[SONG_INFO]", ignoreCase = true)
        ) {
            val trackerBlock = parseTrackerMusic(text)
            if (trackerBlock != null) return trackerBlock
        }

        val lines = text
            .replace("```", "")
            .split(Regex("[|\r\n]+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val headerLine = lines.firstOrNull { it.startsWith("~MUSIC", ignoreCase = true) } ?: return null
        val headerParts = headerLine.removePrefix("~MUSIC").removePrefix("~music").trim().removePrefix(":").split(":")
        if (headerParts.size < 2) return null

        val bpm = headerParts.getOrNull(0)?.trim()?.toIntOrNull()?.coerceIn(40, 240) ?: 120
        val pokemonName = headerParts.getOrNull(1)?.trim() ?: ""
        val key = headerParts.getOrNull(2)?.trim()?.ifBlank { "C_MAJOR" } ?: "C_MAJOR"
        val section = headerParts.getOrNull(3)?.trim()?.ifBlank { "INTRO" } ?: "INTRO"
        val status = headerParts.getOrNull(4)?.trim()?.uppercase() ?: "CONTINUE"

        var notesSummary: String? = null
        val notes = mutableListOf<MusicNote>()
        var maxTick = 0

        for (line in lines) {
            val cleanLine = line.trim()
            if (cleanLine.startsWith("~NOTES:", ignoreCase = true) || cleanLine.startsWith("-NOTES:", ignoreCase = true) || cleanLine.startsWith("NOTES:", ignoreCase = true)) {
                notesSummary = cleanLine.substringAfter("NOTES:", "").trim().takeIf { it.isNotBlank() }
            } else if (cleanLine.startsWith("-V", ignoreCase = true)) {
                val after = cleanLine.substring(2).trim().removePrefix(":")
                val parts = after.split(":")
                if (parts.size >= 3) {
                    val tick = parts[0].trim().toIntOrNull()?.coerceAtLeast(0) ?: 0
                    val note = parts[1].trim().toIntOrNull()?.coerceIn(0, 24) ?: 12
                    val dur = parts[2].trim().toIntOrNull()?.coerceIn(1, 40) ?: 4
                    val lyric = parts.drop(3).joinToString(":").trim().takeIf { it.isNotBlank() }
                    notes.add(MusicNote(tick, note, dur, isVocal = true, lyric = lyric))
                    if (tick + dur > maxTick) maxTick = tick + dur
                }
            } else if (cleanLine.startsWith("-I", ignoreCase = true)) {
                val after = cleanLine.substring(2).trim().removePrefix(":")
                val parts = after.split(":")
                if (parts.size >= 4) {
                    val inst = parts[0].trim().lowercase()
                    val tick = parts[1].trim().toIntOrNull()?.coerceAtLeast(0) ?: 0
                    val note = parts[2].trim().toIntOrNull()?.coerceIn(0, 24) ?: 12
                    val dur = parts[3].trim().toIntOrNull()?.coerceIn(1, 40) ?: 4
                    notes.add(MusicNote(tick, note, dur, isVocal = false, instrument = inst))
                    if (tick + dur > maxTick) maxTick = tick + dur
                }
            }
        }

        if (notes.isEmpty()) return null
        val totalTicks = maxOf(maxTick + 6, 40)
        return MusicBlock(bpm, pokemonName, key, section, status, notes.sortedBy { it.tick }, totalTicks, notesSummary)
    }

    data class KeyScale(
        val keyName: String,
        val description: String
    )

    val AVAILABLE_KEYS = mapOf(
        "C_MAJOR" to KeyScale(
            "C_MAJOR",
            "Scale Semitones: C=12, D=14, E=16, F=17, G=19, A=21, B=23, High C=24. Bass notes: C=0, D=2, E=4, F=5, G=7, A=9, B=11"
        ),
        "A_MINOR" to KeyScale(
            "A_MINOR",
            "Scale Semitones: A=9, B=11, C=12, D=14, E=16, F=17, G=19, High A=21. Bass notes: A=9, D=2, E=4, F=5, G=7, C=0"
        ),
        "G_MAJOR" to KeyScale(
            "G_MAJOR",
            "Scale Semitones: G=7, A=9, B=11, C=12, D=14, E=16, F#=18, High G=19. Bass notes: G=7, C=0, D=2, E=4, A=9"
        ),
        "D_MINOR" to KeyScale(
            "D_MINOR",
            "Scale Semitones: D=14, E=16, F=17, G=19, A=21, Bb=22, C=24. Bass notes: D=2, G=7, A=9, Bb=10, F=5"
        ),
        "E_MINOR" to KeyScale(
            "E_MINOR",
            "Scale Semitones: E=16, F#=18, G=19, A=21, B=23, C=24. Bass notes: E=4, A=9, B=11, C=0, D=2, G=7"
        ),
        "F_MAJOR" to KeyScale(
            "F_MAJOR",
            "Scale Semitones: F=17, G=19, A=21, Bb=22, C=24. Bass notes: F=5, Bb=10, C=0, D=2, G=7, A=9"
        )
    )

    data class MusicStylePreset(
        val genre: String,
        val defaultKey: String,
        val bpm: Int,
        val promptDescription: String,
        val drumSuggestion: String,
        val instruments: List<String>
    )

    val STYLE_PRESETS = listOf(
        MusicStylePreset("pop", "C_MAJOR", 125, "Bright playful pop melody with bouncing accompaniment", "P1 = B . H . S . H . B . B . S . H .\nP2 = B . H . S . H . B . . . S . H .", listOf("harp", "bass", "pling")),
        MusicStylePreset("rock", "D_MINOR", 135, "Energetic driving rock groove with punchy percussion and heavy bass", "P1 = B . . . S . . . B . B . S . . .\nP2 = B . H . S . H . B . B . S . H .", listOf("guitar", "bass", "snare")),
        MusicStylePreset("jazz", "F_MAJOR", 120, "Smooth jazzy swing with walking bass and syncopated chords", "P1 = B . . H . . B . . H B . . . H .\nP2 = B . . . S . . . B . . H S . . .", listOf("bass", "harp", "flute")),
        MusicStylePreset("lofi", "A_MINOR", 120, "Relaxed lofi chillhop with flowing piano chords and steady beat", "P1 = B . H . S . H . B . . . S . H .\nP2 = B . . . S . . . B . H . S . . .", listOf("harp", "bass", "bell")),
        MusicStylePreset("chiptune", "E_MINOR", 150, "Fast bouncy 8-bit arcade melody with rapid punchy beat", "P1 = B . H H S . H . B B H . S . H H\nP2 = B . H . S . H . B . H . S . S .", listOf("bit", "bass", "pling")),
        MusicStylePreset("ballad", "D_MINOR", 120, "Emotional melodic ballad with flowing arpeggios and vocal harmony", "P1 = B . . . S . . . B . . . S . H .\nP2 = B . H . S . . . B . . . S . . .", listOf("harp", "flute", "bass")),
        MusicStylePreset("adventure", "G_MAJOR", 130, "Heroic adventure theme with triumphant rhythm and marching pulse", "P1 = B . H . S . H . B B H . S . H .\nP2 = B . H . S . H . B . . . S . H .", listOf("bell", "harp", "bass")),
        MusicStylePreset("bossa", "F_MAJOR", 125, "Bouncy bossa nova groove with syncopated rhythm and lively guitar", "P1 = B . . H . B . H B . . H . B . H\nP2 = B . . H . . B . . . B . S . . .", listOf("guitar", "bass", "flute")),
        MusicStylePreset("folk", "G_MAJOR", 120, "Acoustic folk tune with active melodic plucking and lively bounce", "P1 = B . . . S . . . B . . . S . . .\nP2 = B . H . S . . . B . H . S . . .", listOf("banjo", "bass", "flute"))
    )

    data class ChatHistoryEntry(val message: String, val timestamp: Long)
    private val recentPlayerChatHistory = ConcurrentHashMap<UUID, MutableList<ChatHistoryEntry>>()

    fun recordPlayerChatMessage(playerUuid: UUID, message: String) {
        val list = recentPlayerChatHistory.computeIfAbsent(playerUuid) { mutableListOf() }
        synchronized(list) {
            val now = System.currentTimeMillis()
            list.removeIf { now - it.timestamp > 120_000L }
            list.add(ChatHistoryEntry(message, now))
            if (list.size > 5) list.removeAt(0)
        }
    }

    fun getPlayerChatRequestText(playerUuid: UUID, explicitRequest: String?): String {
        val list = recentPlayerChatHistory[playerUuid]
        val now = System.currentTimeMillis()
        if (list != null) {
            synchronized(list) {
                list.removeIf { now - it.timestamp > 120_000L }
                val found = list.asReversed().firstOrNull { entry ->
                    val lower = entry.message.lowercase()
                    lower.contains("bpm") ||
                    lower.contains("jazz") || lower.contains("lofi") || lower.contains("lo-fi") ||
                    lower.contains("rock") || lower.contains("pop") || lower.contains("blues") ||
                    lower.contains("ballad") || lower.contains("folk") || lower.contains("chiptune") ||
                    lower.contains("samba") || lower.contains("bossa") || lower.contains("tango") ||
                    lower.contains("valsa") || lower.contains("waltz") || lower.contains("metal") ||
                    lower.contains("triste") || lower.contains("feliz") || lower.contains("alegre") ||
                    lower.contains("animad") || lower.contains("lenta") || lower.contains("rápida") ||
                    lower.contains("rapida") || lower.contains("épic") || lower.contains("epic") ||
                    lower.contains("musica") || lower.contains("música") || lower.contains("song") ||
                    lower.contains("cancao") || lower.contains("canção") || lower.contains("toca") ||
                    lower.contains("tocar") || lower.contains("canta") || lower.contains("cantar") ||
                    lower.contains("ritmo") || lower.contains("estilo")
                }?.message
                if (found != null) {
                    list.clear()
                    return found
                }
            }
        }
        return if (!explicitRequest.isNullOrBlank() && explicitRequest != "Play a fun melody" && explicitRequest != "Perform a full song") {
            explicitRequest
        } else ""
    }

    /**
     * Builds a prompt for the AI to compose Part 1 of a full song (Bars 1 to 8: Intro & Verse).
     */
    fun buildSongPrompt(
        pokemonName: String,
        species: String,
        cryDurationTicks: Int,
        preset: MusicStylePreset,
        playerInstruction: String
    ): String {
        val key = preset.defaultKey
        val bpm = preset.bpm
        val keyDesc = AVAILABLE_KEYS[key]?.description ?: AVAILABLE_KEYS["C_MAJOR"]!!.description
        val isCustom = preset.genre.equals("custom", ignoreCase = true)
        val styleHeader = if (isCustom) {
            "Style/Genre: CUSTOM REQUEST: \"$playerInstruction\" (Adapt the melody, lyrics, rhythm and vibe of this song!)"
        } else {
            "Style/Genre: ${preset.genre.uppercase()} (${preset.promptDescription})"
        }
        val themeHeader = if (isCustom) {
            "Theme/Request: Recreate and adapt the melody, lyrics, rhythm and mood of \"$playerInstruction\" using NoteBlocks and Pokémon voice!"
        } else if (playerInstruction.isNotBlank()) {
            "Theme/Request: $playerInstruction"
        } else {
            "Theme/Request: ${preset.promptDescription}"
        }
        val instList = preset.instruments.filter { it != "bass" && it != "snare" && it != "hat" && it != "kick" && it != "basedrum" }
        val primaryInst = instList.firstOrNull()?.uppercase() ?: "HARP"
        val secondaryInst = instList.getOrNull(1)?.uppercase()

        return """
[POKEMON SONG TRACKER - PART 1/2: INTRO & VERSE (BARS 1 TO 8)]
Performer: $pokemonName (Species: $species)
Tempo: $bpm BPM | Key: $key ($keyDesc)
$styleHeader
$themeHeader
Recommended Instruments: BASS, $primaryInst${if (secondaryInst != null) ", $secondaryInst" else ""}

RHYTHM & SOUND RULES (Grid Tracker):
1. GRID: Every pattern has 16 steps (0 to 15). Steps 0, 4, 8, 12 are downbeats (tempos 1, 2, 3, 4).
2. SOUND SUSTAIN & DENSITY (Minecraft NoteBlock Rule):
   - NoteBlocks and Pokémon cries decay quickly (< 1 second) with NO sustain pedal.
   - KEEP NOTE DENSITY HIGH! Use continuous 8th or 16th-note pulses for bass and chords (e.g. 0:12 | 2:16 | 4:19 | 6:16 | 8:12 | 10:16 | 12:19 | 14:16).
   - NEVER leave long empty silences between notes, as it makes the song sound sparse.
3. DRUMS: 16 tokens separated by spaces.
   - B = Basedrum/Kick (downbeats 0, 8)
   - S = Snare (backbeats 4, 12)
   - H = Hi-Hat (steady pulse 2, 6, 10, 14 or offbeats)
   - . = Silence/Rest
   Suggested Drum Patterns:
${preset.drumSuggestion}
4. BASS (Required):
   - MUST anchor root/fifth notes on downbeats (steps 0, 4, 8, 12) to lock the groove!
   - Format: P1 = 0:<note> | 4:<note> | 8:<note> | 12:<note>
5. MELODY & CHORDS ($primaryInst${if (secondaryInst != null) " / $secondaryInst" else ""}):
   - Main chord/harmonic notes MUST align on downbeats (0, 4, 8, 12) with the drums and bass!
   - For flowing arpeggios, use even steps: 0:note | 2:note | 4:note | 6:note or 8:note | 10:note | 12:note.
6. VOICE (Pokémon cry/singing):
   - Format: P1 = 0:<note>:<short syllable> | 4:<note>:<short syllable>
   - Sing lyrics or Pokémon name syllables (e.g. "$species!", "Pika", "Bulba", "Cry!").
7. TIMELINE (Bars 1 to 8):
   - Structure for Part 1: Intro (Bars 1-2) -> Verse 1 (Bars 3-8).
   - Use pattern names (P1, P2) or -- for silence.

FORMAT EXAMPLE:
[SONG_INFO]
TEMPO: $bpm
KEY: $key
POKEMON: $pokemonName
NOTES: Part 1 Intro and Verse in ${preset.genre.uppercase()}

[DRUM_PATTERNS]
${preset.drumSuggestion}

[BASS_PATTERNS]
P1 = 0:7 | 4:7 | 8:9 | 12:9
P2 = 0:12 | 4:12 | 8:7 | 12:7

[VOICE_PATTERNS]
P1 = 0:12:$species | 4:14:yeah | 8:16:sing | 12:19:go!
P2 = 0:16:$species | 4:14:cry | 8:12:power!

[${primaryInst}_PATTERNS]
P1 = 0:12 | 4:16 | 8:19 | 12:16
P2 = 0:19 | 4:16 | 8:14 | 12:12

[TIMELINE]
BARS:  1,  2,  3,  4,  5,  6,  7,  8
DRUMS: P1, P1, P1, P1, P2, P1, P1, P2
BASS:  P1, P1, P1, P1, P2, P2, P1, P2
VOICE: --, --, P1, P2, P1, P2, P1, P2
$primaryInst: P1, P1, P1, P2, P1, P2, P1, P2

OUTPUT ONLY THE BLOCKS ABOVE. NO CHAT OR EXPLANATION.
""".trimIndent()
    }

    /**
     * Builds a prompt for the AI to compose Part 2 of a full song (Bars 9 to 16: Chorus & Finale).
     */
    fun buildSongContinuationPrompt(perf: ActivePerformance, species: String = "Pokemon"): String {
        val keyDesc = AVAILABLE_KEYS[perf.key]?.description ?: AVAILABLE_KEYS["C_MAJOR"]!!.description
        val part1Bars = if (perf.totalBars > 0) perf.totalBars else 8
        val nextBarStart = part1Bars + 1
        val nextBarEnd = part1Bars + 8
        val barHeader = (nextBarStart..nextBarEnd).joinToString(",  ") { it.toString() }
        val customThemeLine = if (perf.customTheme.isNotBlank()) "\nTheme/Request: Continue the melody, hook and lyrics of \"${perf.customTheme}\"!" else ""

        return """
[POKEMON SONG TRACKER - PART 2/2: CHORUS & FINALE (BARS $nextBarStart TO $nextBarEnd)]
Performer: ${perf.pokemonName} (Species: $species)
Tempo: ${perf.bpm} BPM | Key: ${perf.key} ($keyDesc)$customThemeLine

Here is your PART 1 composition (Bars 1 to $part1Bars):
```text
${perf.block1RawText.trim()}
```

INSTRUCTIONS FOR PART 2 (FINAL PART):
1. CONTINUE from Bar $nextBarStart to Bar $nextBarEnd (8 bars).
2. MUSICAL STRUCTURE:
   - Bars $nextBarStart-${nextBarStart + 3}: CHORUS (High energy, catchy vocal hooks, driving bass, full drums).
   - Bars ${nextBarStart + 4}-${nextBarEnd - 1}: BRIDGE / SOLO (Melodic variation or solo fills).
   - Bar $nextBarEnd: OUTRO & FINAL HIT (Vocal resolve and final note on downbeat step 0).
3. NOTE DENSITY:
   - Keep note density active and full (flowing arpeggios, pulsing bass) so note blocks don't fade into dead silence!
4. PATTERNS:
   - You may REUSE patterns defined in Part 1 (e.g. P1, P2) or define NEW patterns (e.g. P3, P4).
   - Downbeat Lock: Bass and melody chords MUST hit on downbeats (steps 0, 4, 8, 12).
5. TIMELINE:
   - Timeline MUST list exactly 8 bars for Bars $nextBarStart to $nextBarEnd.

FORMAT EXAMPLE:
[SONG_INFO]
TEMPO: ${perf.bpm}
KEY: ${perf.key}
POKEMON: ${perf.pokemonName}
NOTES: Chorus and Finale

[DRUM_PATTERNS]
P3 = B . H H S . H . B B H . S . H H

[BASS_PATTERNS]
P3 = 0:7 | 4:7 | 8:9 | 12:9

[VOICE_PATTERNS]
P3 = 0:19:Chorus! | 4:21:Sing | 8:24:loud! | 12:19:Yeah!

[HARP_PATTERNS]
P3 = 0:12 | 4:16 | 8:19 | 12:24

[TIMELINE]
BARS:  $barHeader
DRUMS: P3, P3, P3, P3, P1, P1, P3, P2
BASS:  P3, P3, P3, P3, P1, P1, P3, P1
VOICE: P3, P3, P1, P3, P3, P3, P1, --
HARP:  P3, P3, P1, P1, P3, P3, P1, P3

OUTPUT ONLY THE BLOCKS ABOVE. NO CHAT OR EXPLANATION.
""".trimIndent()
    }

    /**
     * Builds a prompt for the AI to compose a short melody lasting ~10 seconds (4-5 bars).
     */
    fun buildMusicPrompt(
        pokemonName: String,
        species: String,
        cryDurationTicks: Int,
        preset: MusicStylePreset,
        playerInstruction: String
    ): String {
        val key = preset.defaultKey
        val bpm = preset.bpm
        val keyDesc = AVAILABLE_KEYS[key]?.description ?: AVAILABLE_KEYS["C_MAJOR"]!!.description
        val isCustom = preset.genre.equals("custom", ignoreCase = true)
        val styleHeader = if (isCustom) {
            "Style/Genre: CUSTOM REQUEST: \"$playerInstruction\" (Adapt the melody, rhythm and vibe!)"
        } else {
            "Style/Genre: ${preset.genre.uppercase()} (${preset.promptDescription})"
        }
        val themeHeader = if (isCustom) {
            "Theme/Request: Recreate and adapt the melody, rhythm and vibe of \"$playerInstruction\" using NoteBlocks and Pokémon voice!"
        } else if (playerInstruction.isNotBlank()) {
            "Theme/Request: $playerInstruction"
        } else {
            "Theme/Request: ${preset.promptDescription}"
        }
        val instList = preset.instruments.filter { it != "bass" && it != "snare" && it != "hat" && it != "kick" && it != "basedrum" }
        val primaryInst = instList.firstOrNull()?.uppercase() ?: "HARP"

        return """
[POKEMON MUSIC TRACKER - 10 SECONDS (4-5 BARS)]
Performer: $pokemonName (Species: $species)
Tempo: $bpm BPM | Key: $key ($keyDesc)
$styleHeader
$themeHeader
Recommended Instruments: BASS, $primaryInst

RHYTHM & SOUND RULES (Grid Tracker):
1. GRID: Every pattern has 16 steps (0 to 15). Steps 0, 4, 8, 12 are downbeats (tempos 1, 2, 3, 4).
2. SOUND SUSTAIN & DENSITY (Minecraft NoteBlock Rule):
   - NoteBlocks and Pokémon cries decay quickly (< 1 second) with NO sustain pedal.
   - KEEP NOTE DENSITY HIGH! Use continuous 8th or 16th-note pulses for bass and accompaniment.
   - NEVER leave long empty silences between notes.
3. DRUMS: 16 tokens separated by spaces.
   - B = Basedrum/Kick (downbeats 0, 8), S = Snare (backbeats 4, 12), H = Hi-Hat (2, 6, 10, 14), . = Rest
   Suggested Drum Patterns:
${preset.drumSuggestion}
4. BASS & MELODY ($primaryInst):
   - MUST anchor on downbeats (steps 0, 4, 8, 12) so instruments lock tightly with the drums!
   - Format: P1 = 0:<note 0-24> | 4:<note 0-24> | 8:<note 0-24> | 12:<note 0-24>
5. VOICE:
   - Format: P1 = 0:<note 0-24>:<short lyric/cry>
6. TIMELINE:
   - Schedule 4 to 5 Bars. Use pattern names (P1, P2) or -- for silence.

FORMAT EXAMPLE:
[SONG_INFO]
TEMPO: $bpm
KEY: $key
POKEMON: $pokemonName
NOTES: Short ${preset.genre.uppercase()} groove in $key

[DRUM_PATTERNS]
${preset.drumSuggestion}

[BASS_PATTERNS]
P1 = 0:7 | 4:7 | 8:9 | 12:9
P2 = 0:12 | 4:12 | 8:7 | 12:7

[VOICE_PATTERNS]
P1 = 0:12:$species | 4:14:pi | 8:16:ka! | 12:19:chu!
P2 = 0:16:$species | 4:14:ka | 8:12:chuuu!

[${primaryInst}_PATTERNS]
P1 = 0:12 | 4:16 | 8:19 | 12:16
P2 = 0:19 | 4:16 | 8:14 | 12:12

[TIMELINE]
BARS:  1,  2,  3,  4,  5
DRUMS: P1, P1, P1, P2, P1
BASS:  P1, P1, P2, P2, P1
VOICE: --, P1, --, P2, P1
$primaryInst: P1, P1, P2, P1, P2

OUTPUT ONLY THE BLOCKS ABOVE. NO CHAT OR EXPLANATION.
""".trimIndent()
    }

    /**
     * Starts or registers a new performance when action "music" (10-second piece) is initiated.
     */
    fun startMusicAction(pokemon: Mob, owner: ServerPlayer, playerRequest: String = "Play a fun melody") {
        if (!ConfigHandler.config.actionSettings.isActionActive("music")) {
            return
        }

        val existing = activePerformances[owner.uuid]
        if (existing != null && !existing.isFinished) {
            return
        }

        val resolvedRequest = getPlayerChatRequestText(owner.uuid, playerRequest)
        val pokeObj = (pokemon as? PokemonEntity)?.pokemon
        val pokemonName = pokeObj?.nickname?.string?.takeIf { it.isNotBlank() } ?: pokeObj?.species?.name ?: pokemon.displayName?.string ?: "Pokemon"
        val species = pokeObj?.species?.name ?: "Pokemon"
        val cryDurationTicks = 15

        val matchedPreset = if (resolvedRequest.isNotBlank()) {
            STYLE_PRESETS.firstOrNull { preset ->
                resolvedRequest.contains(preset.genre, ignoreCase = true)
            }
        } else null

        val effectivePreset: MusicStylePreset
        val notificationMessage: String

        if (matchedPreset != null) {
            effectivePreset = matchedPreset
            val genreName = matchedPreset.genre.replaceFirstChar { it.uppercase() }
            notificationMessage = "§d♪ $pokemonName começou a compor uma música ($genreName, 10s)... ♪"
        } else if (resolvedRequest.isNotBlank()) {
            val lower = resolvedRequest.lowercase()
            val isDarkOrSad = lower.contains("triste") || lower.contains("sad") || lower.contains("dark") || lower.contains("batalha")
            val defaultKey = if (isDarkOrSad) "A_MINOR" else "C_MAJOR"
            effectivePreset = MusicStylePreset(
                genre = "custom",
                defaultKey = defaultKey,
                bpm = 125,
                promptDescription = "Adaptation/composition of: \"$resolvedRequest\"",
                drumSuggestion = "P1 = B . H . S . H . B . B . S . H .\nP2 = B . H . S . H . B . . . S . H .",
                instruments = listOf("harp", "bass", "pling", "flute")
            )
            val displayReq = if (resolvedRequest.length > 32) resolvedRequest.take(29) + "..." else resolvedRequest
            notificationMessage = "§d♪ $pokemonName começou a compor a música pedida ($displayReq, 10s)... ♪"
        } else {
            val randomPreset = STYLE_PRESETS.random()
            effectivePreset = randomPreset
            val genreName = randomPreset.genre.replaceFirstChar { it.uppercase() }
            notificationMessage = "§d♪ $pokemonName começou a compor uma música ($genreName, 10s)... ♪"
        }

        val performance = ActivePerformance(
            pokemonUuid = pokemon.uuid,
            ownerUuid = owner.uuid,
            pokemonName = pokemonName,
            bpm = effectivePreset.bpm,
            key = effectivePreset.defaultKey,
            currentSection = "JINGLE",
            isLongSong = false,
            customTheme = resolvedRequest
        )
        activePerformances[owner.uuid] = performance
        CommandState.activeCommands[pokemon.uuid] = "music"

        owner.sendSystemMessage(Component.literal(notificationMessage))

        val prompt = buildMusicPrompt(
            pokemonName = pokemonName,
            species = species,
            cryDurationTicks = cryDurationTicks,
            preset = effectivePreset,
            playerInstruction = resolvedRequest
        )
        DialogueSystem.sendToPlayerBackground?.invoke(owner, prompt)
    }

    /**
     * Starts or registers a new performance when action "song" (full multi-block piece) is initiated.
     */
    fun startSongAction(pokemon: Mob, owner: ServerPlayer, playerRequest: String = "Perform a full song") {
        if (!ConfigHandler.config.actionSettings.isActionActive("song")) {
            return
        }

        val existing = activePerformances[owner.uuid]
        if (existing != null && !existing.isFinished) {
            return
        }

        val resolvedRequest = getPlayerChatRequestText(owner.uuid, playerRequest)
        val pokeObj = (pokemon as? PokemonEntity)?.pokemon
        val pokemonName = pokeObj?.nickname?.string?.takeIf { it.isNotBlank() } ?: pokeObj?.species?.name ?: pokemon.displayName?.string ?: "Pokemon"
        val species = pokeObj?.species?.name ?: "Pokemon"
        val cryDurationTicks = 15

        val matchedPreset = if (resolvedRequest.isNotBlank()) {
            STYLE_PRESETS.firstOrNull { preset ->
                resolvedRequest.contains(preset.genre, ignoreCase = true)
            }
        } else null

        val effectivePreset: MusicStylePreset
        val notificationMessage: String

        if (matchedPreset != null) {
            effectivePreset = matchedPreset
            val genreName = matchedPreset.genre.replaceFirstChar { it.uppercase() }
            notificationMessage = "§d♪ $pokemonName começou a compor uma canção ($genreName, Parte 1/2)... ♪"
        } else if (resolvedRequest.isNotBlank()) {
            val lower = resolvedRequest.lowercase()
            val isDarkOrSad = lower.contains("triste") || lower.contains("sad") || lower.contains("dark") || lower.contains("batalha")
            val defaultKey = if (isDarkOrSad) "A_MINOR" else "C_MAJOR"
            effectivePreset = MusicStylePreset(
                genre = "custom",
                defaultKey = defaultKey,
                bpm = 125,
                promptDescription = "Adaptation/composition of: \"$resolvedRequest\"",
                drumSuggestion = "P1 = B . H . S . H . B . B . S . H .\nP2 = B . H . S . H . B . . . S . H .",
                instruments = listOf("harp", "bass", "pling", "flute")
            )
            val displayReq = if (resolvedRequest.length > 32) resolvedRequest.take(29) + "..." else resolvedRequest
            notificationMessage = "§d♪ $pokemonName começou a compor a canção pedida ($displayReq, Parte 1/2)... ♪"
        } else {
            val randomPreset = STYLE_PRESETS.random()
            effectivePreset = randomPreset
            val genreName = randomPreset.genre.replaceFirstChar { it.uppercase() }
            notificationMessage = "§d♪ $pokemonName começou a compor uma canção ($genreName, Parte 1/2)... ♪"
        }

        val performance = ActivePerformance(
            pokemonUuid = pokemon.uuid,
            ownerUuid = owner.uuid,
            pokemonName = pokemonName,
            bpm = effectivePreset.bpm,
            key = effectivePreset.defaultKey,
            currentSection = "FULL_SONG",
            isLongSong = true,
            customTheme = resolvedRequest
        )
        activePerformances[owner.uuid] = performance
        CommandState.activeCommands[pokemon.uuid] = "song"

        owner.sendSystemMessage(Component.literal(notificationMessage))

        val prompt = buildSongPrompt(
            pokemonName = pokemonName,
            species = species,
            cryDurationTicks = cryDurationTicks,
            preset = effectivePreset,
            playerInstruction = resolvedRequest
        )
        DialogueSystem.sendToPlayerBackground?.invoke(owner, prompt)
    }

    private fun startPlayback(server: MinecraftServer, player: ServerPlayer, performance: ActivePerformance, block: MusicBlock) {
        val actionDesc = if (performance.isLongSong) "cantar uma canção completa" else "tocar uma música"
        val durationSeconds = maxOf(block.totalTicks / 20, 1)
        val durationStr = "${durationSeconds}s"

        if (block.pokemonName.isNotBlank() && (performance.pokemonName == "Pokemon" || performance.pokemonName.isBlank())) {
            performance.pokemonName = block.pokemonName
        }

        if (!performance.isPlaying) {
            performance.isPlaying = true
            performance.blockStartTick = server.tickCount.toLong()
            performance.currentBlockIndex = 0
            performance.lastNoteTickExecuted = -1
            player.sendSystemMessage(Component.literal("§d♪ ${performance.pokemonName} concluiu a composição ($durationStr) e começou a cantar! ♪"))
            println("[CobbleBrain Music] Playback started for ${performance.pokemonName} (Total ticks: ${block.totalTicks}, Notes: ${block.notes.size})")
        }
    }

    /**
     * Handles incoming AI responses containing ~MUSIC or Tracker blocks.
     */
    fun handleMusicResponse(server: MinecraftServer, player: ServerPlayer, content: String) {
        val performance = activePerformances[player.uuid]

        // Part 1 of 2 (Song mode)
        if (performance != null && performance.isLongSong && performance.totalBlocksReceived == 0) {
            val parseResult = parseTrackerMusicWithState(content, baseBarOffset = 0)
            if (parseResult == null) {
                println("[CobbleBrain Music] Failed to parse Part 1 tracker music block: $content")
                player.sendSystemMessage(Component.literal("§c[CobbleBrain] Falha ao processar Parte 1 da canção."))
                return
            }

            performance.block1RawText = content
            performance.drumPatterns.putAll(parseResult.drumPatterns)
            performance.melodicPatterns.putAll(parseResult.melodicPatterns)
            performance.voicePatterns.putAll(parseResult.voicePatterns)
            performance.accumulatedNotes.addAll(parseResult.block.notes)
            performance.totalBars = parseResult.numBars
            performance.totalBlocksReceived = 1
            performance.bpm = parseResult.block.bpm
            performance.key = parseResult.block.key
            performance.waitingSinceTick = server.tickCount.toLong()
            if (parseResult.block.pokemonName.isNotBlank() && (performance.pokemonName == "Pokemon" || performance.pokemonName.isBlank())) {
                performance.pokemonName = parseResult.block.pokemonName
            }

            println("[CobbleBrain Music] Part 1/2 parsed successfully (${parseResult.numBars} bars, ${parseResult.block.notes.size} notes). Requesting Part 2/2...")
            player.sendSystemMessage(Component.literal("§d♪ ${performance.pokemonName} concluiu a Parte 1/2! Compondo a Parte 2/2 (Refrão e Final)... ♪"))

            val pokeObj = server.allLevels.firstNotNullOfOrNull { it.getEntity(performance.pokemonUuid) as? PokemonEntity }?.pokemon
            val species = pokeObj?.species?.name ?: "Pokemon"
            val contPrompt = buildSongContinuationPrompt(performance, species)
            DialogueSystem.sendToPlayerBackground?.invoke(player, contPrompt)
            return
        }

        // Part 2 of 2 (Song mode)
        if (performance != null && performance.isLongSong && performance.totalBlocksReceived == 1) {
            val parseResult = parseTrackerMusicWithState(
                text = content,
                baseBarOffset = performance.totalBars,
                inheritedDrumPatterns = performance.drumPatterns,
                inheritedMelodicPatterns = performance.melodicPatterns,
                inheritedVoicePatterns = performance.voicePatterns,
                inheritedBpm = performance.bpm,
                inheritedKey = performance.key
            )

            if (parseResult == null) {
                println("[CobbleBrain Music] Failed to parse Part 2 tracker music block: $content")
                // Fallback: If Part 2 fails, play Part 1 rather than aborting completely
                if (performance.accumulatedNotes.isNotEmpty()) {
                    val ticksPerStep = 300.0 / performance.bpm.toDouble().coerceIn(40.0, 240.0)
                    val totalTicks = Math.round(performance.totalBars * 16 * ticksPerStep).toInt() + 20
                    val fallbackBlock = MusicBlock(
                        bpm = performance.bpm,
                        pokemonName = performance.pokemonName,
                        key = performance.key,
                        section = "PART1_ONLY",
                        status = "END",
                        notes = performance.accumulatedNotes.sortedBy { it.tick },
                        totalTicks = totalTicks,
                        notesSummary = "Parte 1"
                    )
                    performance.buffer.clear()
                    performance.buffer.add(fallbackBlock)
                    performance.totalBlocksReceived = 2
                    startPlayback(server, player, performance, fallbackBlock)
                }
                return
            }

            performance.accumulatedNotes.addAll(parseResult.block.notes)
            performance.totalBars += parseResult.numBars
            performance.totalBlocksReceived = 2

            val ticksPerStep = 300.0 / performance.bpm.toDouble().coerceIn(40.0, 240.0)
            val totalTicks = Math.round(performance.totalBars * 16 * ticksPerStep).toInt() + 20
            val fullBlock = MusicBlock(
                bpm = performance.bpm,
                pokemonName = performance.pokemonName,
                key = performance.key,
                section = "FULL_SONG",
                status = "END",
                notes = performance.accumulatedNotes.sortedBy { it.tick },
                totalTicks = totalTicks,
                notesSummary = "Canção completa (${performance.totalBars} compassos)"
            )
            performance.buffer.clear()
            performance.buffer.add(fullBlock)
            println("[CobbleBrain Music] Part 2/2 parsed successfully (${parseResult.numBars} bars). Total song: ${performance.totalBars} bars, ${fullBlock.notes.size} notes.")
            startPlayback(server, player, performance, fullBlock)
            return
        }

        // Single block (Music mode / 10s or legacy)
        val block = parseMusicBlock(content)
        if (block == null) {
            println("[CobbleBrain Music] Failed to parse music block from AI response: $content")
            return
        }
        println("[CobbleBrain Music] Parsed single block: Section=${block.section}, Notes=${block.notes.size}, Status=${block.status}, TotalTicks=${block.totalTicks}")

        val perf = performance ?: run {
            val activePoke = PokemonQuery.findActivePokemon(player).firstOrNull {
                it.species.name.equals(block.pokemonName, ignoreCase = true) ||
                it.nickname?.string?.equals(block.pokemonName, ignoreCase = true) == true
            } ?: PokemonQuery.findActivePokemon(player).firstOrNull() ?: return

            val mob = activePoke.entity as? Mob ?: return
            val newPerf = ActivePerformance(
                pokemonUuid = mob.uuid,
                ownerUuid = player.uuid,
                pokemonName = block.pokemonName,
                bpm = block.bpm,
                key = block.key,
                currentSection = block.section,
                isLongSong = false
            )
            activePerformances[player.uuid] = newPerf
            CommandState.activeCommands[mob.uuid] = "song"
            newPerf
        }

        perf.bpm = block.bpm
        perf.key = block.key
        perf.currentSection = block.section
        if (!block.notesSummary.isNullOrBlank()) {
            perf.musicalNotes = block.notesSummary
        }
        perf.buffer.clear()
        perf.buffer.add(block)
        perf.totalBlocksReceived++

        startPlayback(server, player, perf, block)
    }

    /**
     * Ticked every server tick by DialogueSystem.onServerTick.
     */
    fun tick(server: MinecraftServer) {
        if (activePerformances.isEmpty()) return
        val currentTick = server.tickCount.toLong()

        val iterator = activePerformances.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val perf = entry.value

            val player = server.playerList.getPlayer(perf.ownerUuid)
            val pokemon = server.allLevels.firstNotNullOfOrNull { it.getEntity(perf.pokemonUuid) as? Mob }

            if (player == null || pokemon == null || !pokemon.isAlive || pokemon.isRemoved) {
                stopPerformance(server, player, perf)
                iterator.remove()
                continue
            }

            // Audio Choke Handling: Stop previous Pokémon cry when note duration expires
            if (perf.nextChokeTick in 1..currentTick && perf.cryIdToChoke != null) {
                val chokePacket = ClientboundStopSoundPacket(perf.cryIdToChoke, SoundSource.NEUTRAL)
                pokemon.level().players().filter { it.distanceTo(pokemon) < 32.0 }.forEach {
                    (it as? ServerPlayer)?.connection?.send(chokePacket)
                }
                perf.cryIdToChoke = null
                perf.nextChokeTick = -1L
            }

            if (!perf.isPlaying) {
                if (perf.waitingSinceTick > 0L && currentTick - perf.waitingSinceTick > 1200L) { // 60s timeout
                    if (perf.accumulatedNotes.isNotEmpty()) {
                        println("[CobbleBrain Music] Part 2 timed out for ${perf.pokemonName}. Playing Part 1 only.")
                        player.sendSystemMessage(Component.literal("§e♪ ${perf.pokemonName} vai cantar a Parte 1 enquanto prepara novos arranjos! ♪"))
                        val ticksPerStep = 300.0 / perf.bpm.toDouble().coerceIn(40.0, 240.0)
                        val totalTicks = Math.round(perf.totalBars * 16 * ticksPerStep).toInt() + 20
                        val fallbackBlock = MusicBlock(
                            bpm = perf.bpm,
                            pokemonName = perf.pokemonName,
                            key = perf.key,
                            section = "PART1_TIMEOUT",
                            status = "END",
                            notes = perf.accumulatedNotes.sortedBy { it.tick },
                            totalTicks = totalTicks,
                            notesSummary = "Parte 1"
                        )
                        perf.buffer.clear()
                        perf.buffer.add(fallbackBlock)
                        perf.totalBlocksReceived = 2
                        perf.waitingSinceTick = 0L
                        startPlayback(server, player, perf, fallbackBlock)
                    } else {
                        stopPerformance(server, player, perf)
                        iterator.remove()
                    }
                }
                continue
            }

            val currentBlock = perf.buffer.getOrNull(perf.currentBlockIndex)
            if (currentBlock == null) {
                stopPerformance(server, player, perf)
                iterator.remove()
                continue
            }

            if (perf.blockStartTick <= 0L) {
                perf.blockStartTick = currentTick
                perf.lastNoteTickExecuted = -1
            }

            val elapsed = (currentTick - perf.blockStartTick).toInt()

            // Execute all notes that fall into this tick range
            val notesToPlay = currentBlock.notes.filter { it.tick in (perf.lastNoteTickExecuted + 1)..elapsed }
            for (note in notesToPlay) {
                val pitch = noteToPitch(note.note)
                val level = pokemon.level() as? ServerLevel ?: continue

                if (note.isVocal) {
                    // Choke any ongoing cry before starting new note
                    if (perf.cryIdToChoke != null) {
                        val chokePacket = ClientboundStopSoundPacket(perf.cryIdToChoke, SoundSource.NEUTRAL)
                        level.players().filter { it.distanceTo(pokemon) < 32.0 }.forEach {
                            it?.connection?.send(chokePacket)
                        }
                    }

                    val pokeObj = (pokemon as? PokemonEntity)?.pokemon
                    val cryPath = pokeObj?.species?.resourceIdentifier?.path ?: "pikachu"
                    val cryId = ResourceLocation.fromNamespaceAndPath("cobblemon", "pokemon.$cryPath.cry")
                    val crySound = SoundEvent.createVariableRangeEvent(cryId)

                    level.playSound(null, pokemon.blockPosition(), crySound, SoundSource.NEUTRAL, 1.2f, pitch)
                    perf.cryIdToChoke = cryId
                    perf.nextChokeTick = currentTick + note.durationTicks

                    // Particles & Lyrics
                    level.sendParticles(
                        ParticleTypes.NOTE,
                        pokemon.x, pokemon.eyeY + 0.6, pokemon.z,
                        1,
                        note.note / 24.0, 0.0, 0.0,
                        1.0
                    )

                    if (!note.lyric.isNullOrBlank()) {
                        player.sendSystemMessage(Component.literal("§d♪ ${perf.pokemonName}: §f${note.lyric} §d♪"), true)

                        if (ConfigHandler.config.chatbubbles) {
                            DialogueSystem.spawnEntitySpeechBubble(server, pokemon, note.lyric, note.durationTicks + 10)
                        }
                    }
                } else {
                    // Instrument accompaniment (vanilla NoteBlock volume is 3.0f)
                    val instSound = getInstrumentSound(note.instrument ?: "harp")
                    level.playSound(null, pokemon.blockPosition(), instSound, SoundSource.RECORDS, 3.0f, pitch)
                }
            }
            perf.lastNoteTickExecuted = elapsed

            // Block completion check
            if (elapsed >= currentBlock.totalTicks) {
                perf.currentBlockIndex++
                perf.blockStartTick = currentTick
                perf.lastNoteTickExecuted = -1

                if (perf.currentBlockIndex >= perf.buffer.size) {
                    val lastBuffered = perf.buffer.lastOrNull()
                    if (lastBuffered?.status.equals("END", ignoreCase = true) || perf.totalBlocksReceived >= perf.maxBlocks) {
                        stopPerformance(server, player, perf)
                        iterator.remove()
                    }
                }
            }
        }
    }

    /**
     * Stops the performance gracefully.
     */
    fun stopPerformance(server: MinecraftServer, player: ServerPlayer?, perf: ActivePerformance) {
        perf.isFinished = true
        CommandState.activeCommands[perf.pokemonUuid] = "idle"

        if (perf.cryIdToChoke != null) {
            val chokePacket = ClientboundStopSoundPacket(perf.cryIdToChoke, SoundSource.NEUTRAL)
            val pokemon = server.allLevels.firstNotNullOfOrNull { it.getEntity(perf.pokemonUuid) }
            if (pokemon != null) {
                pokemon.level().players().filter { it.distanceTo(pokemon) < 32.0 }.forEach {
                    (it as? ServerPlayer)?.connection?.send(chokePacket)
                }
            }
        }

        player?.sendSystemMessage(Component.literal("§7♪ A performance de ${perf.pokemonName} terminou. ♪"))
    }

    fun stopPerformanceFor(pokemonUuid: UUID) {
        val perf = activePerformances.values.firstOrNull { it.pokemonUuid == pokemonUuid } ?: return
        perf.isFinished = true
        CommandState.activeCommands[pokemonUuid] = "idle"
        activePerformances.remove(perf.ownerUuid)
    }
}
