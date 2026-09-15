package vito.cobblebrain.social

import net.minecraft.resources.ResourceLocation
import vito.cobblebrain.model.StorySerializer
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile

object StoryAssetManager {

    /** Maps entity network ID (entity.id) -> custom dynamic ResourceLocation */
    val entityTextureOverrides = ConcurrentHashMap<Int, ResourceLocation>()

    /** Cache of resolved ZIP files per story ID */
    private val storyZipCache = ConcurrentHashMap<String, File>()

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
        storyZipCache.clear()
    }

    fun clearZipCache() {
        storyZipCache.clear()
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

    /** Locates a matching storypack ZIP archive by story ID or metadata */
    fun findStoryZipFile(storyId: String): File? {
        val cleanId = storyId.trim()
        if (cleanId.isBlank()) return null
        val safeId = cleanId.lowercase().replace(" ", "_")

        storyZipCache[safeId]?.let { cached ->
            if (cached.exists() && cached.isFile) return cached
            storyZipCache.remove(safeId)
        }

        val storage = StorySerializer.storageDir
        val directCandidates = listOf(
            File(storage, "$safeId.zip"),
            File(storage, "$cleanId.zip"),
            File("cobblebrain/storypacks/$safeId.zip"),
            File("cobblebrain/storypacks/$cleanId.zip"),
            File("cobblebrain-ai/storypacks/$safeId.zip"),
            File("cobblebrain-ai/storypacks/$cleanId.zip")
        )
        for (candidate in directCandidates) {
            if (candidate.exists() && candidate.isFile) {
                storyZipCache[safeId] = candidate
                return candidate
            }
        }

        // Search through all ZIP archives in storageDir
        val zips = storage.listFiles { _, name -> name.endsWith(".zip", ignoreCase = true) } ?: emptyArray()
        for (zip in zips) {
            val nameNoExt = zip.nameWithoutExtension.lowercase().replace(" ", "_")
            if (nameNoExt == safeId || zip.nameWithoutExtension.equals(cleanId, ignoreCase = true)) {
                storyZipCache[safeId] = zip
                return zip
            }
        }

        // Deep check: inspect metadata inside each zip
        for (zip in zips) {
            val meta = StorySerializer.peekZipMetadata(zip)
            if (meta != null) {
                val metaId = meta.id.trim().lowercase().replace(" ", "_")
                val metaName = meta.name.trim().lowercase().replace(" ", "_")
                if (metaId == safeId || metaName == safeId || meta.id.equals(cleanId, ignoreCase = true) || meta.name.equals(cleanId, ignoreCase = true)) {
                    storyZipCache[safeId] = zip
                    return zip
                }
            }
        }

        return null
    }

    /**
     * Reads raw PNG bytes of a texture directly from inside a storypack ZIP file without extracting to disk.
     */
    fun loadTextureBytesFromZip(storyId: String, textureName: String): ByteArray? {
        val zipFile = findStoryZipFile(storyId) ?: return null
        val cleanName = textureName.trim().replace('\\', '/')
        val targetFileName = cleanName.substringAfterLast('/').let {
            if (it.endsWith(".png", ignoreCase = true)) it else "$it.png"
        }

        return try {
            ZipFile(zipFile).use { zip ->
                val entries = zip.entries().asSequence().toList()
                // 1. Prefer an entry matching targetFileName that is inside an assets/ or textures/ directory
                val entry = entries.find { e ->
                    if (e.isDirectory) return@find false
                    val path = e.name.replace('\\', '/')
                    val fileName = path.substringAfterLast('/')
                    fileName.equals(targetFileName, ignoreCase = true) &&
                        (path.contains("assets/", ignoreCase = true) || path.contains("textures/", ignoreCase = true))
                } ?: entries.find { e ->
                    // 2. Fallback: any entry matching targetFileName
                    if (e.isDirectory) return@find false
                    val fileName = e.name.replace('\\', '/').substringAfterLast('/')
                    fileName.equals(targetFileName, ignoreCase = true)
                } ?: return null

                zip.getInputStream(entry).use { it.readBytes() }
            }
        } catch (e: Exception) {
            println("[CobbleBrain] Error loading texture '$textureName' from ZIP '${zipFile.name}': ${e.message}")
            null
        }
    }

    /**
     * Backward-compatible delegation to ClientStoryAssetManager on the client side.
     */
    fun getOrCreateDynamicTexture(storyId: String, textureName: String): ResourceLocation? {
        return vito.cobblebrain.client.ClientStoryAssetManager.getOrCreateDynamicTexture(storyId, textureName)
    }
}
