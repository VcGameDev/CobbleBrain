package vito.cobblebrain.engine.checkpoint

import com.cobblemon.mod.common.Cobblemon
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.TagParser
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.storage.LevelResource
import vito.cobblebrain.engine.StoryContext
import vito.cobblebrain.engine.StoryMissionManager
import vito.cobblebrain.model.NodeData
import vito.cobblebrain.model.checkpoint.*
import java.io.File

object StoryCheckpointManager {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    /**
     * Resolves token placeholders in the profile ID, including player tokens and story variables.
     */
    fun resolveProfileId(rawProfileId: String, player: ServerPlayer?, variables: Map<String, Any> = emptyMap()): String {
        val playerStr = player?.scoreboardName ?: "global_player"
        val uuidStr = player?.stringUUID ?: "global_uuid"
        var result = rawProfileId
            .replace("{player}", playerStr)
            .replace("{player_name}", playerStr)
            .replace("{player_uuid}", uuidStr)
            .replace("{uuid}", uuidStr)

        if (variables.isNotEmpty() && result.contains("{")) {
            variables.forEach { (k, v) ->
                result = result.replace("{$k}", v.toString())
            }
        }

        return result.trim().ifBlank { "checkpoint_1" }
    }

    /**
     * Gets the file handle for a given checkpoint scope and resolved profile ID.
     */
    fun getCheckpointFile(server: MinecraftServer, scope: String, profileId: String): File {
        val worldDir = try {
            server.getWorldPath(LevelResource.ROOT).toFile()
        } catch (e: Exception) {
            File("saves/world")
        }
        val safeScope = if (scope.equals("GLOBAL", ignoreCase = true)) "GLOBAL" else "PLAYER"
        val dir = File(worldDir, "cobblebrain/checkpoints/$safeScope")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val safeFileName = profileId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        return File(dir, "$safeFileName.json")
    }

    /**
     * Checks if a checkpoint save file exists on disk.
     */
    fun hasCheckpoint(server: MinecraftServer, scope: String, rawProfileId: String, player: ServerPlayer? = null, variables: Map<String, Any> = emptyMap()): Boolean {
        val profileId = resolveProfileId(rawProfileId, player, variables)
        val file = getCheckpointFile(server, scope, profileId)
        return file.exists()
    }

    /**
     * Directly saves a StoryCheckpointData instance to disk safely.
     */
    fun saveCheckpointData(server: MinecraftServer, scope: String, rawProfileId: String, data: StoryCheckpointData, player: ServerPlayer? = null): Boolean {
        val profileId = resolveProfileId(rawProfileId, player, data.variables)
        return try {
            val file = getCheckpointFile(server, scope, profileId)
            val json = gson.toJson(data)
            file.writeText(json)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Saves a checkpoint to disk from a story context and node configuration.
     */
    fun saveCheckpoint(context: StoryContext, node: NodeData): Boolean {
        val server = context.server ?: context.player?.server ?: return false
        val player = context.player
        val rawProfileId = node.params["profileId"]?.ifBlank { "checkpoint_1" } ?: "checkpoint_1"
        val scope = node.params["scope"] ?: "PLAYER"
        val modulesStr = node.params["modules"] ?: "ALL"

        val profileId = resolveProfileId(rawProfileId, player, context.variables)
        val selectedModules = modulesStr.split(",").map { it.trim().uppercase() }.toSet()
        val isAll = selectedModules.contains("ALL") || selectedModules.isEmpty()

        // 1. Variables Snapshot
        val variablesData = if (isAll || selectedModules.contains("VARIABLES")) {
            HashMap(context.variables)
        } else {
            emptyMap()
        }

        // 2. Player Data Snapshot
        val playerData = if ((isAll || selectedModules.contains("PLAYER")) && player != null) {
            val dimKey = player.level().dimension().location().toString()
            val saveMainInv = node.params["saveMainInventory"] != "false"
            val saveArmorOffhand = node.params["saveArmorOffhand"] != "false"

            val registryProvider = player.registryAccess()
            val mainInvList = if (saveMainInv) {
                (0 until player.inventory.items.size).mapNotNull { idx ->
                    val stack = player.inventory.items[idx]
                    if (stack.isEmpty) null
                    else {
                        val tag = stack.save(registryProvider)
                        CheckpointItemData(
                            slot = idx,
                            itemId = BuiltInRegistries.ITEM.getKey(stack.item).toString(),
                            count = stack.count,
                            nbt = tag.toString()
                        )
                    }
                }
            } else null

            val armorOffhandList = if (saveArmorOffhand) {
                val list = mutableListOf<CheckpointItemData>()
                player.inventory.armor.forEachIndexed { idx, stack ->
                    if (!stack.isEmpty) {
                        val tag = stack.save(registryProvider)
                        list.add(CheckpointItemData(
                            slot = idx,
                            itemId = BuiltInRegistries.ITEM.getKey(stack.item).toString(),
                            count = stack.count,
                            nbt = tag.toString()
                        ))
                    }
                }
                player.inventory.offhand.forEachIndexed { idx, stack ->
                    if (!stack.isEmpty) {
                        val tag = stack.save(registryProvider)
                        list.add(CheckpointItemData(
                            slot = 10 + idx,
                            itemId = BuiltInRegistries.ITEM.getKey(stack.item).toString(),
                            count = stack.count,
                            nbt = tag.toString()
                        ))
                    }
                }
                list
            } else null

            CheckpointPlayerData(
                position = CheckpointPlayerPosition(
                    x = player.x,
                    y = player.y,
                    z = player.z,
                    pitch = player.xRot,
                    yaw = player.yRot,
                    dimension = dimKey
                ),
                stats = CheckpointPlayerStats(
                    health = player.health,
                    hunger = player.foodData.foodLevel
                ),
                mainInventory = mainInvList,
                armorAndOffhand = armorOffhandList
            )
        } else {
            null
        }

        // 3. Cobblemon Party Snapshot
        val cobblemonParty = if ((isAll || selectedModules.contains("COBBLEMON") || selectedModules.contains("POKEMON")) && player != null) {
            try {
                val party = Cobblemon.storage.getParty(player)
                (0..5).mapNotNull { idx ->
                    val poke = party.get(idx) ?: return@mapNotNull null
                    val movesList = poke.moveSet.mapNotNull { m -> m?.name }
                    CheckpointPokemonData(
                        species = poke.species.name,
                        level = poke.level,
                        currentHp = poke.currentHealth,
                        maxHp = poke.maxHealth,
                        nickname = poke.nickname?.string,
                        status = poke.status?.toString(),
                        moves = movesList
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

        // 4. World State Snapshot
        val worldState = if (isAll || selectedModules.contains("WORLD")) {
            val level = player?.serverLevel() ?: server.overworld()
            CheckpointWorldData(
                timeOfDay = level.dayTime,
                isRaining = level.isRaining,
                isThundering = level.isThundering
            )
        } else {
            null
        }

        // 5. Quest Progress Snapshot
        val questProgress = if (isAll || selectedModules.contains("QUESTS")) {
            HashMap(StoryMissionManager.getQuestProgressSnapshot(player))
        } else {
            emptyMap()
        }

        val checkpointData = StoryCheckpointData(
            profileId = profileId,
            scope = scope,
            savedAt = System.currentTimeMillis(),
            variables = variablesData,
            playerData = playerData,
            cobblemonParty = cobblemonParty,
            worldState = worldState,
            questProgress = questProgress
        )

        return saveCheckpointData(server, scope, profileId, checkpointData, player)
    }

    /**
     * Loads a checkpoint from disk. Returns null if file does not exist.
     */
    fun loadCheckpoint(server: MinecraftServer, player: ServerPlayer?, scope: String, rawProfileId: String, variables: Map<String, Any> = emptyMap()): StoryCheckpointData? {
        val profileId = resolveProfileId(rawProfileId, player, variables)
        val file = getCheckpointFile(server, scope, profileId)
        if (!file.exists()) return null

        return try {
            val json = file.readText()
            gson.fromJson(json, StoryCheckpointData::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Applies deserialized checkpoint data to player, world, variables, and quests.
     */
    fun applyCheckpoint(
        context: StoryContext,
        checkpointData: StoryCheckpointData,
        mergeMode: String = "OVERWRITE",
        gracePeriodTicks: Int = 60,
        cleanStoryTag: String = ""
    ) {
        val server = context.server ?: context.player?.server ?: return
        val player = context.player

        // 1. Merge Variables
        if (checkpointData.variables.isNotEmpty()) {
            if (mergeMode.equals("SOFT_MERGE", ignoreCase = true)) {
                checkpointData.variables.forEach { (k, v) ->
                    if (!context.variables.containsKey(k)) {
                        context.variables[k] = v
                    }
                }
            } else { // OVERWRITE
                context.variables.putAll(checkpointData.variables)
            }
        }

        // 2. Entity Cleanup by Story Tag
        if (cleanStoryTag.isNotBlank()) {
            try {
                val cmd = "kill @e[tag=$cleanStoryTag]"
                server.commands.performPrefixedCommand(
                    player?.createCommandSourceStack()?.withPermission(4)?.withSuppressedOutput()
                        ?: server.createCommandSourceStack().withPermission(4).withSuppressedOutput(),
                    cmd
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 3. Player Teleportation & Stats
        val pData = checkpointData.playerData
        if (pData != null && player != null) {
            val pos = pData.position
            val stats = pData.stats

            try {
                val targetDimLoc = ResourceLocation.tryParse(pos.dimension)
                val targetLevel = if (targetDimLoc != null) {
                    val key = ResourceKey.create(Registries.DIMENSION, targetDimLoc)
                    server.getLevel(key) ?: player.serverLevel()
                } else {
                    player.serverLevel()
                }

                player.teleportTo(
                    targetLevel,
                    pos.x, pos.y, pos.z,
                    pos.yaw, pos.pitch
                )
                player.health = stats.health.coerceIn(1.0f, player.maxHealth)
                player.foodData.foodLevel = stats.hunger.coerceIn(0, 20)
                if (gracePeriodTicks > 0) {
                    player.invulnerableTime = gracePeriodTicks
                }

                val registryProvider = player.registryAccess()
                // Restore Main Inventory independently if snapshot was captured
                if (pData.mainInventory != null) {
                    player.inventory.items.clear()
                    pData.mainInventory.forEach { itemSnap ->
                        try {
                            val stack = if (!itemSnap.nbt.isNullOrBlank()) {
                                val opt = ItemStack.parse(registryProvider, TagParser.parseTag(itemSnap.nbt))
                                if (opt.isPresent) opt.get() else ItemStack.EMPTY
                            } else {
                                val res = ResourceLocation.tryParse(itemSnap.itemId)
                                if (res != null) ItemStack(BuiltInRegistries.ITEM.get(res), itemSnap.count) else ItemStack.EMPTY
                            }
                            if (!stack.isEmpty && itemSnap.slot in 0 until player.inventory.items.size) {
                                player.inventory.items[itemSnap.slot] = stack
                            }
                        } catch (_: Exception) {}
                    }
                }

                // Restore Armor & Offhand independently if snapshot was captured
                if (pData.armorAndOffhand != null) {
                    player.inventory.armor.clear()
                    player.inventory.offhand.clear()
                    pData.armorAndOffhand.forEach { itemSnap ->
                        try {
                            val stack = if (!itemSnap.nbt.isNullOrBlank()) {
                                val opt = ItemStack.parse(registryProvider, TagParser.parseTag(itemSnap.nbt))
                                if (opt.isPresent) opt.get() else ItemStack.EMPTY
                            } else {
                                val res = ResourceLocation.tryParse(itemSnap.itemId)
                                if (res != null) ItemStack(BuiltInRegistries.ITEM.get(res), itemSnap.count) else ItemStack.EMPTY
                            }
                            if (!stack.isEmpty) {
                                if (itemSnap.slot in 0 until player.inventory.armor.size) {
                                    player.inventory.armor[itemSnap.slot] = stack
                                } else if (itemSnap.slot >= 10 && (itemSnap.slot - 10) in 0 until player.inventory.offhand.size) {
                                    player.inventory.offhand[itemSnap.slot - 10] = stack
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 4. Cobblemon Party State Restoration
        if (checkpointData.cobblemonParty.isNotEmpty() && player != null) {
            try {
                val party = Cobblemon.storage.getParty(player)
                checkpointData.cobblemonParty.forEachIndexed { idx, pSnap ->
                    if (idx in 0..5) {
                        val poke = party.get(idx)
                        if (poke != null) {
                            poke.currentHealth = pSnap.currentHp.coerceIn(0, poke.maxHealth)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 5. World State Restoration
        val wState = checkpointData.worldState
        if (wState != null) {
            try {
                val level = player?.serverLevel() ?: server.overworld()
                level.dayTime = wState.timeOfDay
                val weatherType = when {
                    wState.isThundering -> "thunder"
                    wState.isRaining -> "rain"
                    else -> "clear"
                }
                server.commands.performPrefixedCommand(
                    server.createCommandSourceStack().withPermission(4).withSuppressedOutput(),
                    "weather $weatherType 6000"
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 6. Quest Progress Restoration
        if (checkpointData.questProgress.isNotEmpty() && player != null) {
            StoryMissionManager.restoreQuestProgress(player, checkpointData.questProgress)
        }
    }
}
