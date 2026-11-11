package vito.cobblebrain.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.io.PrintWriter
import java.util.UUID

data class AiProfile(
    var id: String = UUID.randomUUID().toString().take(8),
    var name: String = "Default",
    var preferredName: String = "",
    var psychicTranslation: Boolean = false,
    var showHunger: Boolean = false,
    var instruct: List<String> = CobblebrainClientConfig.DEFAULT_INSTRUCT,
    var playerAbout: List<String> = CobblebrainClientConfig.DEFAULT_PLAYER_ABOUT,
    var useDefaultOutput: Boolean = true,
    var outputFormat: String = CobblebrainClientConfig.DEFAULT_OUTPUT_FORMAT,
    var outputDialogue: Boolean = true,
    var outputActions: Boolean = true,
    var outputFriendship: Boolean = true,
    var outputWorldContext: Boolean = true,
    var outputGuaranteedCatch: Boolean = true,
    var outputMobsContext: Boolean = true,
    var outputQuests: Boolean = true,
    var enableTraits: Boolean = true,
    var maxPokemonLikes: Int = 5,
    var maxPokemonDislikes: Int = 5,
    var outputMemories: String = "AVERAGE",
    var outputPokemonLanguage: Boolean = false,
    var enableRecap: Boolean = true
) {
    var maxAiLikes: Int
        get() = maxPokemonLikes
        set(v) { maxPokemonLikes = v }
    var maxAiDislikes: Int
        get() = maxPokemonDislikes
        set(v) { maxPokemonDislikes = v }
    val isMemoriesEnabled: Boolean
        get() = !outputMemories.equals("OFF", ignoreCase = true)

    fun deepCopy(): AiProfile {
        return AiProfile(
            id = UUID.randomUUID().toString().take(8),
            name = if (name.endsWith(" (Copy)")) name else "$name (Copy)",
            preferredName = preferredName,
            psychicTranslation = psychicTranslation,
            showHunger = showHunger,
            instruct = instruct.toList(),
            playerAbout = playerAbout.toList(),
            useDefaultOutput = useDefaultOutput,
            outputFormat = outputFormat,
            outputDialogue = outputDialogue,
            outputActions = outputActions,
            outputFriendship = outputFriendship,
            outputWorldContext = outputWorldContext,
            outputGuaranteedCatch = outputGuaranteedCatch,
            outputMobsContext = outputMobsContext,
            outputQuests = outputQuests,
            enableTraits = enableTraits,
            maxPokemonLikes = maxPokemonLikes,
            maxPokemonDislikes = maxPokemonDislikes,
            outputMemories = outputMemories,
            outputPokemonLanguage = outputPokemonLanguage,
            enableRecap = enableRecap
        )
    }
}

object AiProfileManager {
    private val profilesFile = File("config/cobblebrain_ai_profiles.json")
    val profiles = mutableListOf<AiProfile>()
    var activeProfileId: String = "default"
        set(value) {
            field = value
            try {
                ClientConfigHandler.clientConfig.activeAiProfileId = value
            } catch (_: Throwable) {}
        }

    fun isModDefaultProfile(p: AiProfile?): Boolean {
        if (p == null) return false
        return p.id == "default" || p.name.equals("Mod Default", ignoreCase = true) || p.name.equals("Default", ignoreCase = true)
    }

    fun syncModDefaultProfile(p: AiProfile, presetMode: String = try { ClientConfigHandler.clientConfig.presetMode } catch (_: Throwable) { "Normal" }) {
        p.id = "default"
        p.name = "Mod Default"
        if (p.preferredName.isBlank()) {
            p.preferredName = try { ClientConfigHandler.clientConfig.preferredName } catch (_: Throwable) { "" }
        }
        p.psychicTranslation = try { ClientConfigHandler.clientConfig.psychicTranslation } catch (_: Throwable) { false }
        p.showHunger = try { ClientConfigHandler.clientConfig.showHunger } catch (_: Throwable) { false }
        if (p.instruct.isEmpty()) {
            p.instruct = CobblebrainClientConfig.DEFAULT_INSTRUCT
        }
        if (p.playerAbout.isEmpty()) {
            p.playerAbout = CobblebrainClientConfig.DEFAULT_PLAYER_ABOUT
        }
        p.useDefaultOutput = true
        p.outputFormat = CobblebrainClientConfig.DEFAULT_OUTPUT_FORMAT
        p.outputDialogue = true
        p.outputActions = true
        p.outputFriendship = true
        p.outputWorldContext = true
        p.outputGuaranteedCatch = true
        p.outputQuests = true
        p.enableTraits = true
        p.maxPokemonLikes = 5
        p.maxPokemonDislikes = 5
        p.outputPokemonLanguage = false

        when (presetMode) {
            "Light" -> {
                p.outputMobsContext = false
                p.outputMemories = "OFF"
            }
            "Custom" -> {
                val srv = try { ConfigHandler.config } catch (_: Throwable) { null }
                p.outputDialogue = srv?.outputDialogue ?: true
                p.outputActions = srv?.outputActions ?: true
                p.outputFriendship = srv?.outputFriendship ?: true
                p.outputWorldContext = srv?.outputWorldContext ?: true
                p.outputGuaranteedCatch = srv?.outputGuaranteedCatch ?: true
                p.outputMobsContext = srv?.outputMobsContext ?: true
                p.outputQuests = srv?.outputQuests ?: true
                p.enableTraits = srv?.enableTraits ?: true
                p.maxPokemonLikes = srv?.maxPokemonLikes ?: 5
                p.maxPokemonDislikes = srv?.maxPokemonDislikes ?: 5
                p.outputMemories = srv?.outputMemories ?: "AVERAGE"
                p.outputPokemonLanguage = srv?.outputPokemonLanguage ?: false
                p.enableRecap = srv?.enableRecap ?: true
                p.useDefaultOutput = true
            }
            else -> {
                // "Smart", "Normal", "Offline"
                p.outputMobsContext = true
                p.outputMemories = "AVERAGE"
                p.enableRecap = true
            }
        }
    }

    fun isProfileModifiedFromPreset(p: AiProfile, mode: String = try { ClientConfigHandler.clientConfig.presetMode } catch (_: Throwable) { "Normal" }): Boolean {
        return when (mode) {
            "Light" -> {
                p.outputMobsContext ||
                p.outputMemories != "OFF" ||
                !p.outputDialogue ||
                !p.outputActions ||
                !p.outputFriendship ||
                !p.outputWorldContext ||
                !p.outputGuaranteedCatch ||
                !p.outputQuests ||
                !p.enableTraits ||
                !p.useDefaultOutput ||
                p.outputPokemonLanguage ||
                !p.enableRecap
            }
            "Smart", "Normal" -> {
                !p.outputMobsContext ||
                p.outputMemories != "AVERAGE" ||
                !p.outputDialogue ||
                !p.outputActions ||
                !p.outputFriendship ||
                !p.outputWorldContext ||
                !p.outputGuaranteedCatch ||
                !p.outputQuests ||
                !p.enableTraits ||
                !p.useDefaultOutput ||
                p.outputPokemonLanguage ||
                !p.enableRecap
            }
            else -> false
        }
    }

    fun getActiveProfile(): AiProfile {
        return profiles.find { it.id == activeProfileId }
            ?: profiles.find { isModDefaultProfile(it) } ?: profiles.firstOrNull()
            ?: createDefaultProfile()
    }

    fun setActiveProfile(profileId: String) {
        val target = profiles.find { it.id == profileId } ?: return
        activeProfileId = target.id
        applyActiveProfileToClient()
        save()
    }

    fun createDefaultProfile(): AiProfile {
        val p = AiProfile(
            id = "default",
            name = "Mod Default"
        )
        syncModDefaultProfile(p)
        if (profiles.none { it.id == p.id }) {
            profiles.add(0, p)
        }
        activeProfileId = p.id
        return p
    }

    fun applyActiveProfileToClient() {
        val p = getActiveProfile()
        try {
            val cfg = ClientConfigHandler.clientConfig
            cfg.preferredName = p.preferredName
            cfg.psychicTranslation = p.psychicTranslation
            cfg.showHunger = p.showHunger
            cfg.instruct = ClientConfigHandler.ensureCreativePrompt(p.instruct)
            cfg.playerAbout = ClientConfigHandler.ensurePlayerAboutHeader(p.playerAbout)
            cfg.outputFormat = p.outputFormat
            cfg.activeAiProfileId = p.id
            cfg.enableRecap = p.enableRecap
            ClientConfigHandler.save()

            val srvCfg = ConfigHandler.config
            srvCfg.useDefaultOutput = p.useDefaultOutput
            srvCfg.outputDialogue = p.outputDialogue
            srvCfg.outputActions = p.outputActions
            srvCfg.outputFriendship = p.outputFriendship
            srvCfg.outputWorldContext = p.outputWorldContext
            srvCfg.outputGuaranteedCatch = p.outputGuaranteedCatch
            srvCfg.outputMobsContext = p.outputMobsContext
            srvCfg.outputQuests = p.outputQuests
            srvCfg.enableTraits = p.enableTraits
            srvCfg.maxPokemonLikes = p.maxPokemonLikes
            srvCfg.maxPokemonDislikes = p.maxPokemonDislikes
            srvCfg.outputMemories = p.outputMemories
            srvCfg.outputPokemonLanguage = p.outputPokemonLanguage
            srvCfg.enableRecap = p.enableRecap
            ConfigHandler.save()

            if (isModDefaultProfile(p) && cfg.presetMode != "Custom") {
                cfg.isCustomizedFromPreset = isProfileModifiedFromPreset(p, cfg.presetMode)
                ClientConfigHandler.save()
            }
        } catch (_: Throwable) {}
    }

    fun load() {
        profiles.clear()
        if (profilesFile.exists()) {
            try {
                val text = profilesFile.readText()
                val obj = JsonParser.parseString(text).asJsonObject
                if (obj.has("activeProfileId")) {
                    activeProfileId = obj.get("activeProfileId").asString
                }
                if (obj.has("profiles")) {
                    val arr = obj.getAsJsonArray("profiles")
                    val gson = GsonBuilder().create()
                    for (elem in arr) {
                        if (elem.isJsonObject) {
                            val pObj = elem.asJsonObject
                            if (pObj.has("outputMemories") && pObj.get("outputMemories").isJsonPrimitive) {
                                val prim = pObj.getAsJsonPrimitive("outputMemories")
                                if (prim.isBoolean) {
                                    pObj.addProperty("outputMemories", if (prim.asBoolean) "AVERAGE" else "OFF")
                                }
                            }
                        }
                        val profile = gson.fromJson(elem, AiProfile::class.java)
                        if (profile != null) {
                            if (isModDefaultProfile(profile)) {
                                profile.id = "default"
                                profile.name = "Mod Default"
                            }
                            profiles.add(profile)
                        }
                    }
                }
            } catch (e: Exception) {
                println("[CobbleBrain] Error loading ai_profiles: ${e.message}")
            }
        }

        if (profiles.isEmpty()) {
            createDefaultProfile()
            save()
        } else {
            if (profiles.none { isModDefaultProfile(it) }) {
                val p = AiProfile(id = "default", name = "Mod Default")
                syncModDefaultProfile(p)
                profiles.add(0, p)
            }
            if (profiles.none { it.id == activeProfileId }) {
                val savedId = try { ClientConfigHandler.clientConfig.activeAiProfileId } catch (_: Throwable) { "default" }
                activeProfileId = if (profiles.any { it.id == savedId }) savedId else (profiles.find { isModDefaultProfile(it) }?.id ?: profiles.first().id)
            }
        }
        applyActiveProfileToClient()
    }

    fun save() {
        try {
            profilesFile.parentFile?.mkdirs()
            val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
            val obj = JsonObject()
            obj.addProperty("activeProfileId", activeProfileId)
            obj.add("profiles", gson.toJsonTree(profiles))
            vito.cobblebrain.social.DiskWriteExecutor.submit {
                try {
                    PrintWriter(profilesFile).use { pw ->
                        pw.print(gson.toJson(obj))
                    }
                } catch (e: Exception) {
                    println("[CobbleBrain] Error saving ai_profiles: ${e.message}")
                }
            }
        } catch (e: Exception) {
            println("[CobbleBrain] Failed to schedule save ai_profiles: ${e.message}")
        }
    }
}
