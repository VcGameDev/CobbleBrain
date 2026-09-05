package vito.cobblebrain.social

import net.minecraft.resources.ResourceLocation
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object StoryAssetManager {

    /** Maps entity network ID (entity.id) -> custom dynamic ResourceLocation */
    val entityTextureOverrides = ConcurrentHashMap<Int, ResourceLocation>()

    @JvmStatic
    fun getEntityTextureOverride(entityId: Int): ResourceLocation? {
        return entityTextureOverrides[entityId]
    }

    fun setEntityOverride(entityId: Int, textureLocation: ResourceLocation) {
        entityTextureOverrides[entityId] = textureLocation
    }

    fun clearEntityOverride(entityId: Int) {
        entityTextureOverrides.remove(entityId)
    }

    @Suppress("unused")
    fun clearAllOverrides() {
        entityTextureOverrides.clear()
    }

    /** Resolves the asset directories where textures can be placed for a story */
    fun getStoryTextureDirs(storyId: String): List<File> {
        val safeStoryId = storyId.trim().lowercase().replace(" ", "_")
        val dirs = mutableListOf<File>()

        // 1. Direct modular storypack folder assets: cobblebrain/storypacks/<story_id>/assets/
        dirs.add(File("cobblebrain/storypacks/$safeStoryId/assets"))
        dirs.add(File("cobblebrain/storypacks/$safeStoryId/assets/textures"))

        // 2. saves/<current_world>/cobblebrain/stories/<story_id>/assets/textures/ (world-specific)
        dirs.add(File("cobblebrain/stories/$safeStoryId/assets/textures"))

        // 3. cobblebrain/storypacks/assets/<story_id>/textures/
        dirs.add(File("cobblebrain/storypacks/assets/$safeStoryId/textures"))

        // 4. Fallback global texture folder: cobblebrain/storypacks/assets/textures/
        dirs.add(File("cobblebrain/storypacks/assets/textures"))

        // 5. Legacy cobblebrain-ai fallbacks
        dirs.add(File("cobblebrain-ai/storypacks/$safeStoryId/assets/textures"))
        dirs.add(File("cobblebrain-ai/storypacks/$safeStoryId/assets"))
        dirs.add(File("cobblebrain-ai/stories/$safeStoryId/assets/textures"))

        return dirs
    }

    /** Returns the primary directory to store/create textures for a story */
    fun getPrimaryTextureDir(storyId: String): File {
        val dirs = getStoryTextureDirs(storyId)
        val target = dirs.firstOrNull { it.exists() } ?: dirs.first()
        if (!target.exists()) {
            target.mkdirs()
        }
        return target
    }

    /** Lists all .png textures found across the story assets directories */
    fun listStoryTextures(storyId: String): List<File> {
        val dirs = getStoryTextureDirs(storyId)
        val files = mutableListOf<File>()
        val seenNames = mutableSetOf<String>()

        for (dir in dirs) {
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles { _, name -> name.endsWith(".png", ignoreCase = true) }?.forEach { f ->
                    if (seenNames.add(f.name.lowercase())) {
                        files.add(f)
                    }
                }
            }
        }
        return files.sortedBy { it.name.lowercase() }
    }

    /** Finds a specific texture file by name across candidate directories */
    fun findTextureFile(storyId: String, textureName: String): File? {
        val cleanName = textureName.trim()
        val safeName = if (cleanName.endsWith(".png", ignoreCase = true)) cleanName else "$cleanName.png"

        val dirs = getStoryTextureDirs(storyId)
        for (dir in dirs) {
            val candidate = File(dir, safeName)
            if (candidate.exists() && candidate.isFile) {
                return candidate
            }
        }
        return null
    }

    /**
     * Backward-compatible delegation to ClientStoryAssetManager on the client side.
     */
    fun getOrCreateDynamicTexture(storyId: String, textureName: String): ResourceLocation? {
        return vito.cobblebrain.client.ClientStoryAssetManager.getOrCreateDynamicTexture(storyId, textureName)
    }
}
