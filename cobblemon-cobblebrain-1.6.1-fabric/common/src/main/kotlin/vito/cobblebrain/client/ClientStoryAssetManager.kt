package vito.cobblebrain.client

import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.ResourceLocation
import vito.cobblebrain.social.StoryAssetManager
import java.io.FileInputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Client-only texture registrar for dynamic storypack textures.
 * Strictly avoids classloading Blaze3D / Minecraft texture classes on dedicated servers.
 */
object ClientStoryAssetManager {

    /** Cache of registered dynamic textures: "storyId/textureName" -> ResourceLocation */
    private val dynamicTextureCache = ConcurrentHashMap<String, ResourceLocation>()

    /**
     * Loads a PNG image into Minecraft's TextureManager as a DynamicTexture and returns its ResourceLocation.
     * Must ONLY be called on the client side.
     */
    fun getOrCreateDynamicTexture(storyId: String, textureName: String): ResourceLocation? {
        val cleanName = textureName.trim()
        val safeFileName = if (cleanName.endsWith(".png", ignoreCase = true)) cleanName else "$cleanName.png"
        val safeStoryId = storyId.trim().lowercase().replace(" ", "_")
        val cacheKey = "$safeStoryId/$safeFileName".lowercase()

        dynamicTextureCache[cacheKey]?.let { return it }

        val file = StoryAssetManager.findTextureFile(storyId, safeFileName) ?: return null

        return try {
            val mc = Minecraft.getInstance() ?: return null
            val inputStream = FileInputStream(file)
            val nativeImage = NativeImage.read(inputStream)
            inputStream.close()

            val dynamicTexture = DynamicTexture(nativeImage)
            val resourcePath = "storypacks/${safeStoryId.filter { it.isLetterOrDigit() || it == '_' }}/${safeFileName.removeSuffix(".png").filter { it.isLetterOrDigit() || it == '_' }}"
            val textureLocation = ResourceLocation("cobblebrain", resourcePath)

            mc.textureManager.register(textureLocation, dynamicTexture)
            dynamicTextureCache[cacheKey] = textureLocation
            textureLocation
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }

    fun clearCache() {
        dynamicTextureCache.clear()
    }
}
