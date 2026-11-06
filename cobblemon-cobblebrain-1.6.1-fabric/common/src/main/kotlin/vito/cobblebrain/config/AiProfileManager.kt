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
    var outputMemories: Boolean = true,
    var outputPokemonLanguage: Boolean = false
) {
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
            outputMemories = outputMemories,
            outputPokemonLanguage = outputPokemonLanguage
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

    fun getActiveProfile(): AiProfile {
        return profiles.find { it.id == activeProfileId }
            ?: profiles.find { it.id == "default" } ?: profiles.firstOrNull()
            ?: createDefaultProfile()
    }

    fun setActiveProfile(profileId: String) {
        val target = profiles.find { it.id == profileId } ?: return
        activeProfileId = target.id
        applyActiveProfileToClient()
        save()
    }

    fun createDefaultProfile(): AiProfile {
        val srv = try { ConfigHandler.config } catch (_: Throwable) { null }
        val p = AiProfile(
            id = "default",
            name = "Default",
            preferredName = try { ClientConfigHandler.clientConfig.preferredName } catch (_: Throwable) { "" },
            psychicTranslation = try { ClientConfigHandler.clientConfig.psychicTranslation } catch (_: Throwable) { false },
            showHunger = try { ClientConfigHandler.clientConfig.showHunger } catch (_: Throwable) { false },
            instruct = try { ClientConfigHandler.clientConfig.instruct } catch (_: Throwable) { CobblebrainClientConfig.DEFAULT_INSTRUCT },
            playerAbout = try { ClientConfigHandler.clientConfig.playerAbout } catch (_: Throwable) { CobblebrainClientConfig.DEFAULT_PLAYER_ABOUT },
            useDefaultOutput = srv?.useDefaultOutput ?: true,
            outputFormat = try { ClientConfigHandler.clientConfig.outputFormat } catch (_: Throwable) { CobblebrainClientConfig.DEFAULT_OUTPUT_FORMAT },
            outputDialogue = srv?.outputDialogue ?: true,
            outputActions = srv?.outputActions ?: true,
            outputFriendship = srv?.outputFriendship ?: true,
            outputWorldContext = srv?.outputWorldContext ?: true,
            outputGuaranteedCatch = srv?.outputGuaranteedCatch ?: true,
            outputMobsContext = srv?.outputMobsContext ?: true,
            outputQuests = srv?.outputQuests ?: true,
            enableTraits = srv?.enableTraits ?: true,
            outputMemories = srv?.outputMemories ?: true,
            outputPokemonLanguage = srv?.outputPokemonLanguage ?: false
        )
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
            srvCfg.outputMemories = p.outputMemories
            srvCfg.outputPokemonLanguage = p.outputPokemonLanguage
            ConfigHandler.save()
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
                        val profile = gson.fromJson(elem, AiProfile::class.java)
                        if (profile != null) {
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
        } else if (profiles.none { it.id == activeProfileId }) {
            val savedId = try { ClientConfigHandler.clientConfig.activeAiProfileId } catch (_: Throwable) { "default" }; activeProfileId = if (profiles.any { it.id == savedId }) savedId else (profiles.find { it.id == "default" }?.id ?: profiles.first().id)
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
