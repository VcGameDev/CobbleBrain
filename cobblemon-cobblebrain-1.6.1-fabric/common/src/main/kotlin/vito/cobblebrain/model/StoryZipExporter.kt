package vito.cobblebrain.model

import net.minecraft.client.Minecraft
import org.lwjgl.system.MemoryStack
import org.lwjgl.util.tinyfd.TinyFileDialogs
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AccessDeniedException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.CompletableFuture
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object StoryZipExporter {

    /**
     * Opens the native OS file dialog ("Save As") to choose destination ZIP file.
     * Must be called from a background thread to prevent freezing Minecraft's main render loop.
     */
    fun promptSaveZipLocation(defaultFilename: String): File? {
        val filterDesc = "ZIP Archive (*.zip)"
        val defaultDir = File(System.getProperty("user.home"), "Desktop")
        val defaultPath = if (defaultDir.exists()) {
            File(defaultDir, defaultFilename).absolutePath
        } else {
            File(System.getProperty("user.home"), defaultFilename).absolutePath
        }

        val chosenPath: String? = try {
            MemoryStack.stackPush().use { stack ->
                val aFilterPatterns = stack.mallocPointer(1)
                aFilterPatterns.put(stack.UTF8("*.zip"))
                aFilterPatterns.flip()
                TinyFileDialogs.tinyfd_saveFileDialog(
                    "Export Storypack as ZIP",
                    defaultPath,
                    aFilterPatterns,
                    filterDesc
                )
            }
        } catch (_: Throwable) {
            TinyFileDialogs.tinyfd_saveFileDialog(
                "Export Storypack as ZIP",
                defaultPath,
                null,
                filterDesc
            )
        }

        if (chosenPath.isNullOrBlank()) return null

        var finalFile = File(chosenPath.trim())
        if (!finalFile.name.endsWith(".zip", ignoreCase = true)) {
            finalFile = File(finalFile.parentFile, "${finalFile.name}.zip")
        }
        return finalFile
    }

    /**
     * Recursively compresses a storypack directory into a target ZIP file.
     * Uses a temporary .tmp file to ensure atomicity and avoid corrupted outputs.
     */
    fun zipStorypackDirectory(sourceDir: File, targetZipFile: File) {
        if (!sourceDir.exists() || !sourceDir.isDirectory) {
            throw IllegalArgumentException("Storypack directory does not exist or is not a folder: ${sourceDir.absolutePath}")
        }

        val parentDir = targetZipFile.parentFile ?: File(".")
        if (!parentDir.exists()) {
            parentDir.mkdirs()
        }

        val tempZip = File(parentDir, "${targetZipFile.name}.${System.currentTimeMillis()}.tmp")

        try {
            ZipOutputStream(FileOutputStream(tempZip).buffered()).use { zos ->
                val sourcePath = sourceDir.toPath()
                Files.walk(sourcePath).use { stream ->
                    stream.filter { !Files.isDirectory(it) }.forEach { filePath ->
                        val relativePath = sourcePath.relativize(filePath).toString().replace('\\', '/')
                        // Exclude any accidental temp files or hidden git directories
                        if (!relativePath.endsWith(".tmp", ignoreCase = true) && !relativePath.startsWith(".git")) {
                            val entry = ZipEntry(relativePath)
                            zos.putNextEntry(entry)
                            Files.copy(filePath, zos)
                            zos.closeEntry()
                        }
                    }
                }
                zos.finish()
            }

            // Move temp file to final destination
            try {
                Files.move(
                    tempZip.toPath(),
                    targetZipFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: Throwable) {
                // Fallback without ATOMIC_MOVE if cross-filesystem or unsupported
                Files.move(
                    tempZip.toPath(),
                    targetZipFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
        } catch (e: Exception) {
            if (tempZip.exists()) {
                try { tempZip.delete() } catch (_: Throwable) {}
            }
            if (e is AccessDeniedException || e is FileSystemException) {
                throw IllegalStateException("File is currently locked or in use by another program (e.g. WinRAR/7-Zip): ${targetZipFile.name}", e)
            }
            throw e
        }
    }

    /**
     * Asynchronously executes the save dialog and ZIP export in a background thread.
     * Callbacks are dispatched back to the Minecraft client thread.
     */
    fun exportProjectToZipAsync(
        project: StoryProject,
        onSuccess: (File) -> Unit,
        onError: (String) -> Unit,
        onCancel: () -> Unit = {}
    ) {
        val safeName = project.id.ifBlank { project.name }.trim().lowercase().replace(" ", "_")
        val defaultFilename = "$safeName.zip"

        // Ensure pack directory exists or locate it
        val packDir = project.packDirectory
            ?: File(StorySerializer.storageDir, safeName)

        if (!packDir.exists() || !packDir.isDirectory) {
            onError("Storypack folder not found. Please save the project first.")
            return
        }

        CompletableFuture.runAsync {
            try {
                val targetZip = promptSaveZipLocation(defaultFilename)
                if (targetZip == null) {
                    Minecraft.getInstance().execute {
                        onCancel()
                    }
                    return@runAsync
                }

                // Ensure legacy "cenas" directory is migrated to "scenes" before zipping
                val legacyCenas = File(packDir, "cenas")
                val scenesFolder = File(packDir, "scenes")
                if (!scenesFolder.exists() && legacyCenas.exists() && legacyCenas.isDirectory) {
                    legacyCenas.renameTo(scenesFolder)
                }

                zipStorypackDirectory(packDir, targetZip)

                Minecraft.getInstance().execute {
                    onSuccess(targetZip)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val friendlyMessage = e.message ?: "Unknown compression failure"
                Minecraft.getInstance().execute {
                    onError(friendlyMessage)
                }
            }
        }
    }
}
