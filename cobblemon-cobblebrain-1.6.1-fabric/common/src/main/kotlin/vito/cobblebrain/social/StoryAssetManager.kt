package vito.cobblebrain.social

import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.ResourceLocation
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.ConcurrentHashMap

object StoryAssetManager {

    /** Maps entity network ID (entity.id) -> custom dynamic ResourceLocation */
    val entityTextureOverrides = ConcurrentHashMap<Int, ResourceLocation>()

    /** Cache of registered dynamic textures: "storyId/textureName" -> ResourceLocation */
    private val dynamicTextureCache = ConcurrentHashMap<String, ResourceLocation>()

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

    fun clearAllOverrides() {
        entityTextureOverrides.clear()
    }

    /** Resolves the asset directories where textures can be placed for a story */
    fun getStoryTextureDirs(storyId: String): List<File> {
        val safeStoryId = storyId.trim().lowercase().replace(" ", "_")
        val dirs = mutableListOf<File>()

        // 1. saves/<current_world>/cobblebrain/stories/<story_id>/assets/textures/
        try {
            val mc = try { Minecraft.getInstance() } catch (_: Throwable) { null }
            val server = mc?.singleplayerServer
            val worldDir = server?.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)?.toFile()
            if (worldDir != null) {
                dirs.add(File(worldDir, "cobblebrain/stories/$safeStoryId/assets/textures"))
            }
        } catch (_: Throwable) {}

        // 2. cobblebrain-ai/stories/<story_id>/assets/textures/
        dirs.add(File("cobblebrain-ai/stories/$safeStoryId/assets/textures"))

        // 3. cobblebrain-ai/storypacks/assets/<story_id>/textures/
        dirs.add(File("cobblebrain-ai/storypacks/assets/$safeStoryId/textures"))

        // 4. Fallback global texture folder: cobblebrain-ai/storypacks/assets/textures/
        dirs.add(File("cobblebrain-ai/storypacks/assets/textures"))

        return dirs
    }

    /** Returns the primary directory to store/create textures for a story */
    fun getPrimaryTextureDir(storyId: String): File {
        val dirs = getStoryTextureDirs(storyId)
        val target = dirs.firstOrNull { it.exists() } ?: dirs.getOrNull(1) ?: dirs.first()
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
     * Loads a PNG image into Minecraft's TextureManager as a DynamicTexture and returns its ResourceLocation.
     * Must be called on the client side.
     */
    fun getOrCreateDynamicTexture(storyId: String, textureName: String): ResourceLocation? {
        val cleanName = textureName.trim()
        val safeFileName = if (cleanName.endsWith(".png", ignoreCase = true)) cleanName else "$cleanName.png"
        val safeStoryId = storyId.trim().lowercase().replace(" ", "_")
        val cacheKey = "$safeStoryId/$safeFileName".lowercase()

        dynamicTextureCache[cacheKey]?.let { return it }

        val file = findTextureFile(storyId, safeFileName) ?: return null

        return try {
            val mc = Minecraft.getInstance() ?: return null
            val inputStream = FileInputStream(file)
            val nativeImage = NativeImage.read(inputStream)
            inputStream.close()

            val dynamicTexture = DynamicTexture(nativeImage)
            val resourcePath = "story_texture/${safeStoryId.filter { it.isLetterOrDigit() || it == '_' }}/${safeFileName.removeSuffix(".png").filter { it.isLetterOrDigit() || it == '_' }}"
            val textureLocation = ResourceLocation("cobblebrain", resourcePath)

            mc.textureManager.register(textureLocation, dynamicTexture)
            dynamicTextureCache[cacheKey] = textureLocation
            textureLocation
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }
}
